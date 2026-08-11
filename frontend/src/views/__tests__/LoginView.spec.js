import { mount } from "@vue/test-utils";
import LoginView from "../LoginView.vue";

const dictionary = {
  loginTitle: "Dealer AI Assistant",
  loginUsernamePlaceholder: "Username",
  loginPasswordPlaceholder: "Password",
  loginButton: "Sign in",
  loginLoading: "Signing in",
  loginNoticeBody: "Authorized users only.",
  loginEyebrow: "System Login"
};

function mountLoginView(props = {}) {
  return mount(LoginView, {
    props: { username: "", password: "", dictionary, locale: "en", ...props }
  });
}

describe("LoginView", () => {
  test("renders username and password fields", () => {
    const wrapper = mountLoginView();
    const inputs = wrapper.findAll(".login-input-field");
    expect(inputs).toHaveLength(2);
    expect(inputs[0].attributes("placeholder")).toBe("Username");
    expect(inputs[1].attributes("placeholder")).toBe("Password");
    expect(inputs[1].attributes("type")).toBe("password");
  });

  test("requires both credentials and blocks duplicate submits while loading", async () => {
    const wrapper = mountLoginView({ username: "admin", password: "secret", loginLoading: true });
    const button = wrapper.find(".login-submit-button");
    expect(button.attributes("disabled")).toBeDefined();
    await button.trigger("click");
    expect(wrapper.emitted("submit")).toBeUndefined();
  });

  test("emits the credential bindings and submit action", async () => {
    const wrapper = mountLoginView({ username: "admin", password: "secret" });
    const inputs = wrapper.findAll(".login-input-field");
    await inputs[0].setValue("analyst");
    await inputs[1].setValue("new-secret");
    await wrapper.find(".login-submit-button").trigger("click");
    expect(wrapper.emitted("update:username")[0]).toEqual(["analyst"]);
    expect(wrapper.emitted("update:password")[0]).toEqual(["new-secret"]);
    expect(wrapper.emitted("submit")).toHaveLength(1);
  });
});
