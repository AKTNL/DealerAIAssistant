import { nextTick } from "vue";
import { mount } from "@vue/test-utils";
import LoginView from "../LoginView.vue";

const dictionary = {
  loginTitle: "Dealer AI Assistant",
  loginBody: "Use your access key to continue.",
  loginPlaceholder: "Access key",
  loginButton: "Sign in",
  loginLoading: "Signing in",
  loginNoticeBody: "Authorized users only.",
  loginEyebrow: "System Login"
};

function mountLoginView(props = {}) {
  return mount(LoginView, {
    attachTo: document.body,
    props: {
      accessKey: "",
      dictionary,
      locale: "en",
      ...props
    }
  });
}

async function flushLoginErrorWatcher() {
  await nextTick();
  await nextTick();
}

describe("LoginView", () => {
  test("renders the glassmorphism login layout", () => {
    const wrapper = mountLoginView();

    expect(wrapper.find(".login-glass-shell").exists()).toBe(true);
    expect(wrapper.find(".login-card").exists()).toBe(true);
    expect(wrapper.find(".login-card-top-bar").exists()).toBe(true);
    expect(wrapper.find(".login-submit-button").exists()).toBe(true);
    expect(wrapper.text()).toContain(dictionary.loginTitle);
    expect(wrapper.text()).toContain(dictionary.loginEyebrow);
    expect(wrapper.text()).toContain(dictionary.loginNoticeBody);
    expect(wrapper.find(".login-input-field").attributes("placeholder")).toBe(
      dictionary.loginPlaceholder
    );
  });

  test("renders the loading state and disables submit while login is in progress", () => {
    const wrapper = mountLoginView({
      loginLoading: true
    });

    const button = wrapper.find(".login-submit-button");

    expect(button.text()).toBe(dictionary.loginLoading);
    expect(button.attributes("disabled")).toBeDefined();
  });

  test("does not emit submit while login is already in progress", async () => {
    const wrapper = mountLoginView({
      loginLoading: true
    });

    await wrapper.find(".login-submit-button").trigger("click");

    expect(wrapper.emitted("submit")).toBeUndefined();
  });

  test("emits submit and replays the same login error without changing existing bindings", async () => {
    const wrapper = mountLoginView({
      accessKey: "secret"
    });

    const input = wrapper.find(".login-input-field");

    expect(wrapper.find(".login-error-text").isVisible()).toBe(false);
    expect(input.classes()).not.toContain("login-input-error");

    await wrapper.setProps({
      loginError: "Invalid access key"
    });
    await flushLoginErrorWatcher();

    expect(wrapper.find(".login-error-text").text()).toBe("Invalid access key");
    expect(input.classes()).toContain("login-input-error");
    expect(wrapper.find(".login-card").classes()).toContain("login-card-shake");
    expect(document.activeElement).toBe(input.element);

    await wrapper.find(".login-card").trigger("animationend");

    expect(wrapper.find(".login-card").classes()).not.toContain("login-card-shake");

    input.element.blur();

    await wrapper.find(".login-submit-button").trigger("click");
    await flushLoginErrorWatcher();

    expect(wrapper.find(".login-error-text").text()).toBe("Invalid access key");
    expect(wrapper.find(".login-card").classes()).toContain("login-card-shake");
    expect(document.activeElement).toBe(input.element);
    expect(wrapper.emitted("submit")).toHaveLength(1);
  });

  test("keeps existing error rendering when mounted with a login error", async () => {
    const wrapper = mountLoginView({
      loginError: "Invalid access key"
    });

    expect(wrapper.find(".login-error-text").text()).toBe("Invalid access key");
  });
});
