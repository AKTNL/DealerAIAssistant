import { mount } from "@vue/test-utils";
import PasswordChangeView from "../PasswordChangeView.vue";

const dictionary = {
  currentPasswordPlaceholder: "Current password",
  logoutButton: "Sign out",
  newPasswordPlaceholder: "New password",
  passwordChangeBody: "Choose a permanent password.",
  passwordChangeButton: "Change password",
  passwordChangeTitle: "Change your password",
  passwordChanging: "Changing password"
};

function mountPasswordChangeView(props = {}) {
  return mount(PasswordChangeView, {
    props: {
      currentPassword: "",
      dictionary,
      newPassword: "",
      ...props
    }
  });
}

describe("PasswordChangeView", () => {
  test("renders current and new password fields", () => {
    const wrapper = mountPasswordChangeView();
    const inputs = wrapper.findAll("input[type='password']");

    expect(inputs).toHaveLength(2);
    expect(inputs[0].attributes("autocomplete")).toBe("current-password");
    expect(inputs[1].attributes("autocomplete")).toBe("new-password");
  });

  test("requires both passwords and blocks submission while loading", async () => {
    const wrapper = mountPasswordChangeView({
      currentPassword: "temporary-password",
      newPassword: "permanent-password",
      loading: true
    });
    const submit = wrapper.find(".login-submit-button");

    expect(submit.attributes("disabled")).toBeDefined();
    await submit.trigger("click");
    expect(wrapper.emitted("submit")).toBeUndefined();
  });

  test("emits password bindings, submission, and sign out", async () => {
    const wrapper = mountPasswordChangeView({
      currentPassword: "temporary-password",
      newPassword: "permanent-password"
    });
    const inputs = wrapper.findAll("input[type='password']");

    await inputs[0].setValue("current-updated");
    await inputs[1].setValue("new-updated");
    await wrapper.find(".login-submit-button").trigger("click");
    await wrapper.find(".login-lang-toggle").trigger("click");

    expect(wrapper.emitted("update:current-password")[0]).toEqual(["current-updated"]);
    expect(wrapper.emitted("update:new-password")[0]).toEqual(["new-updated"]);
    expect(wrapper.emitted("submit")).toHaveLength(1);
    expect(wrapper.emitted("sign-out")).toHaveLength(1);
  });
});
