<script setup>
import { onMounted, ref } from "vue";
import {
  deleteSmtpConfig,
  getSmtpConfig,
  saveSmtpConfig,
  testSmtpConfig
} from "../../api/notificationSmtp";

const props = defineProps({
  dictionary: { type: Object, required: true }
});

const emit = defineEmits(["sign-out"]);
const loading = ref(false);
const pending = ref("");
const error = ref("");
const notice = ref("");
const configured = ref(false);
const form = ref(emptyForm());

onMounted(load);

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const config = await getSmtpConfig();
    configured.value = Boolean(config);
    form.value = config ? {
      host: config.host ?? "",
      port: Number(config.port ?? 587),
      securityMode: config.securityMode ?? "STARTTLS",
      username: config.username ?? "",
      password: "",
      fromAddress: config.fromAddress ?? "",
      fromDisplayName: config.fromDisplayName ?? "",
      enabled: config.enabled === true,
      version: config.version ?? null,
      passwordConfigured: config.passwordConfigured === true
    } : emptyForm();
  } catch (requestError) {
    handleError(requestError);
  } finally {
    loading.value = false;
  }
}

async function save() {
  pending.value = "save";
  clearFeedback();
  try {
    const saved = await saveSmtpConfig({
      host: form.value.host.trim(),
      port: Number(form.value.port),
      securityMode: form.value.securityMode,
      username: form.value.username.trim(),
      password: form.value.password || null,
      fromAddress: form.value.fromAddress.trim(),
      fromDisplayName: form.value.fromDisplayName.trim() || null,
      enabled: form.value.enabled,
      version: form.value.version
    });
    configured.value = true;
    form.value = {
      ...form.value,
      password: "",
      passwordConfigured: saved.passwordConfigured === true,
      version: saved.version,
      enabled: saved.enabled === true
    };
    notice.value = props.dictionary.smtpSaved;
  } catch (requestError) {
    handleError(requestError);
  } finally {
    pending.value = "";
  }
}

async function test() {
  pending.value = "test";
  clearFeedback();
  try {
    const result = await testSmtpConfig();
    notice.value = result?.accepted
      ? props.dictionary.smtpTestAccepted
      : `${props.dictionary.smtpTestFailed}: ${result?.code ?? "SMTP_TEST_FAILED"}`;
  } catch (requestError) {
    handleError(requestError);
  } finally {
    pending.value = "";
  }
}

async function remove() {
  if (typeof window !== "undefined" && !window.confirm(props.dictionary.smtpDeleteConfirm)) {
    return;
  }
  pending.value = "delete";
  clearFeedback();
  try {
    await deleteSmtpConfig(form.value.version);
    configured.value = false;
    form.value = emptyForm();
    notice.value = props.dictionary.smtpDeleted;
  } catch (requestError) {
    handleError(requestError);
  } finally {
    pending.value = "";
  }
}

function selectSecurityMode() {
  form.value.port = form.value.securityMode === "SMTPS" ? 465 : 587;
}

function handleError(requestError) {
  if (requestError?.status === 401) {
    emit("sign-out");
    return;
  }
  error.value = requestError?.message || props.dictionary.smtpRequestError;
}

function clearFeedback() {
  error.value = "";
  notice.value = "";
}

function emptyForm() {
  return {
    host: "",
    port: 587,
    securityMode: "STARTTLS",
    username: "",
    password: "",
    fromAddress: "",
    fromDisplayName: "",
    enabled: true,
    version: null,
    passwordConfigured: false
  };
}
</script>

<template>
  <section class="admin-panel" aria-labelledby="smtp-settings-heading">
    <div class="admin-panel-heading">
      <div>
        <h3 id="smtp-settings-heading">{{ dictionary.smtpTitle }}</h3>
        <p>{{ dictionary.smtpBody }}</p>
      </div>
      <button class="ghost-button" type="button" :disabled="loading || Boolean(pending)" @click="load">
        <span class="material-icons" aria-hidden="true">refresh</span>
        {{ dictionary.adminRefresh }}
      </button>
    </div>

    <p v-if="error" class="admin-feedback admin-feedback-error" role="alert">{{ error }}</p>
    <p v-else-if="notice" class="admin-feedback" role="status">{{ notice }}</p>

    <form v-if="!loading" class="admin-inline-form" @submit.prevent="save">
      <label>
        <span>{{ dictionary.smtpHost }}</span>
        <input v-model="form.host" class="text-input" type="text" required autocomplete="off" />
      </label>
      <label>
        <span>{{ dictionary.smtpSecurityMode }}</span>
        <select v-model="form.securityMode" class="text-input" @change="selectSecurityMode">
          <option value="STARTTLS">STARTTLS</option>
          <option value="SMTPS">SMTPS</option>
        </select>
      </label>
      <label>
        <span>{{ dictionary.smtpPort }}</span>
        <input v-model.number="form.port" class="text-input" type="number" readonly />
      </label>
      <label>
        <span>{{ dictionary.smtpUsername }}</span>
        <input v-model="form.username" class="text-input" type="text" required autocomplete="off" />
      </label>
      <label>
        <span>{{ dictionary.smtpPassword }}</span>
        <input
          v-model="form.password"
          class="text-input"
          type="password"
          :required="!form.passwordConfigured"
          autocomplete="new-password"
        />
      </label>
      <label>
        <span>{{ dictionary.smtpFromAddress }}</span>
        <input v-model="form.fromAddress" class="text-input" type="email" required autocomplete="off" />
      </label>
      <label>
        <span>{{ dictionary.smtpFromDisplayName }}</span>
        <input v-model="form.fromDisplayName" class="text-input" type="text" maxlength="128" />
      </label>
      <label class="subscription-enabled-field">
        <input v-model="form.enabled" type="checkbox" />
        <span>{{ dictionary.smtpEnabled }}</span>
      </label>
      <div class="admin-action-row">
        <button class="primary-button" type="submit" :disabled="Boolean(pending)">
          {{ dictionary.smtpSave }}
        </button>
        <button
          class="ghost-button"
          type="button"
          :disabled="!configured || Boolean(pending)"
          @click="test"
        >
          {{ dictionary.smtpTest }}
        </button>
        <button
          class="admin-danger-button"
          type="button"
          :disabled="!configured || Boolean(pending)"
          @click="remove"
        >
          {{ dictionary.smtpDelete }}
        </button>
      </div>
    </form>
  </section>
</template>
