<script setup>
import { computed, onMounted, ref } from "vue";
import { useReportCollaboration } from "../../composables/useReportCollaboration";
import { renderMarkdownLite } from "../../utils/markdown";

const props = defineProps({
  currentUser: {
    type: Object,
    default: () => ({ permissions: [] })
  },
  dictionary: {
    type: Object,
    required: true
  },
  locale: {
    type: String,
    required: true
  }
});

const emit = defineEmits(["sign-out"]);
const currentUser = computed(() => props.currentUser);
const dictionary = computed(() => props.dictionary);
const commentBody = ref("");

const {
  addComment,
  applyFilters,
  assignees,
  canCollaborate,
  changeAssignee,
  changeStatus,
  clearFilters,
  conflictVersion,
  detailLoading,
  filters,
  hasConflict,
  initialize,
  loadError,
  loading,
  operationError,
  pendingAction,
  reloadConflict,
  reports,
  selectReport,
  selected,
  successMessage
} = useReportCollaboration({
  currentUser,
  dictionary,
  onAuthExpired: () => emit("sign-out")
});

const currentReport = computed(() => selected.value?.report ?? null);
const reportHtml = computed(() => renderMarkdownLite(selected.value?.markdown ?? ""));
const isTerminal = computed(() => ["RESOLVED", "CLOSED"].includes(currentReport.value?.status));
const availableStatuses = computed(() => {
  const transitions = {
    OPEN: ["IN_PROGRESS", "CLOSED"],
    IN_PROGRESS: ["RESOLVED", "CLOSED"]
  };
  return transitions[currentReport.value?.status] ?? [];
});
const assigneeFilterOptions = computed(() => uniqueById(
  reports.value.map((report) => report.assignee).filter(Boolean)
));
const organizationFilterOptions = computed(() => Array.from(new Set(
  reports.value
    .filter((report) => report.scope?.type === "ORGANIZATION")
    .flatMap((report) => String(report.scope?.id ?? "").split(","))
    .filter(Boolean)
)).sort((left, right) => Number(left) - Number(right)));

onMounted(initialize);

async function handleCommentSubmit() {
  const body = commentBody.value.trim();
  if (!body) {
    return;
  }
  const result = await addComment(body);
  if (result) {
    commentBody.value = "";
  }
}

function handleAssigneeChange(event) {
  const value = event.target.value;
  void changeAssignee(value ? Number(value) : null);
}

function statusLabel(status) {
  return props.dictionary.collaborationStatuses?.[status] ?? status;
}

function reportTypeLabel(reportType) {
  return props.dictionary.collaborationReportTypes?.[reportType] ?? reportType;
}

function assigneeLabel(assignee) {
  if (!assignee) {
    return props.dictionary.collaborationUnassigned;
  }
  return assignee.displayName || assignee.username;
}

function actorLabel(event) {
  return event.actorDisplayName || event.actorUsername || props.dictionary.collaborationSystemActor;
}

function eventLabel(event) {
  if (event.type === "STATUS_CHANGED") {
    return props.dictionary.collaborationStatusEvent
      .replace("{from}", statusLabel(event.previousValue))
      .replace("{to}", statusLabel(event.currentValue));
  }
  if (event.type === "ASSIGNEE_CHANGED") {
    return props.dictionary.collaborationAssigneeEvent;
  }
  return props.dictionary.collaborationEvents?.[event.type] ?? event.type;
}

function formatDate(value) {
  if (!value) {
    return props.dictionary.collaborationNotAvailable;
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat(props.locale === "zh" ? "zh-CN" : "en-US", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(date);
}

function scopeLabel(scope) {
  if (scope?.type === "ORGANIZATION") {
    return `${props.dictionary.collaborationOrganization} ${scope.id}`;
  }
  return props.dictionary.collaborationGlobalScope;
}

function uniqueById(values) {
  return Array.from(new Map(values.map((value) => [value.userId, value])).values());
}
</script>

<template>
  <section class="collaboration-view">
    <header class="collaboration-header">
      <div>
        <span class="section-eyebrow">{{ dictionary.collaborationEyebrow }}</span>
        <h1>{{ dictionary.collaborationTitle }}</h1>
        <p>{{ dictionary.collaborationSubtitle }}</p>
      </div>
      <button
        class="secondary-button collaboration-refresh"
        type="button"
        :disabled="loading"
        :title="dictionary.collaborationRefresh"
        @click="initialize"
      >
        <span class="material-icons" aria-hidden="true">refresh</span>
        <span>{{ dictionary.collaborationRefresh }}</span>
      </button>
    </header>

    <form class="collaboration-filters" @submit.prevent="applyFilters">
      <label>
        <span>{{ dictionary.collaborationFilterStatus }}</span>
        <select v-model="filters.status">
          <option value="">{{ dictionary.collaborationFilterAll }}</option>
          <option v-for="status in ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED']" :key="status" :value="status">
            {{ statusLabel(status) }}
          </option>
        </select>
      </label>
      <label>
        <span>{{ dictionary.collaborationFilterAssignee }}</span>
        <select v-model="filters.assigneeUserId">
          <option value="">{{ dictionary.collaborationFilterAll }}</option>
          <option v-for="assignee in assigneeFilterOptions" :key="assignee.userId" :value="assignee.userId">
            {{ assigneeLabel(assignee) }}
          </option>
        </select>
      </label>
      <label>
        <span>{{ dictionary.collaborationFilterOrganization }}</span>
        <select v-model="filters.organizationId">
          <option value="">{{ dictionary.collaborationFilterAll }}</option>
          <option v-for="organizationId in organizationFilterOptions" :key="organizationId" :value="organizationId">
            {{ dictionary.collaborationOrganization }} {{ organizationId }}
          </option>
        </select>
      </label>
      <label>
        <span>{{ dictionary.collaborationGeneratedFrom }}</span>
        <input v-model="filters.generatedFrom" type="date">
      </label>
      <label>
        <span>{{ dictionary.collaborationGeneratedTo }}</span>
        <input v-model="filters.generatedTo" type="date">
      </label>
      <div class="collaboration-filter-actions">
        <button class="primary-button" type="submit" :disabled="loading">
          {{ dictionary.collaborationApplyFilters }}
        </button>
        <button class="text-button" type="button" :disabled="loading" @click="clearFilters">
          {{ dictionary.collaborationClearFilters }}
        </button>
      </div>
    </form>

    <div v-if="loading" class="collaboration-state" role="status">
      <strong>{{ dictionary.collaborationLoadingTitle }}</strong>
      <span>{{ dictionary.collaborationLoadingBody }}</span>
    </div>
    <div v-else-if="loadError" class="collaboration-state collaboration-state-error" role="alert">
      <strong>{{ dictionary.collaborationLoadError }}</strong>
      <span>{{ loadError }}</span>
    </div>
    <div v-else-if="!reports.length" class="collaboration-state">
      <strong>{{ dictionary.collaborationEmptyTitle }}</strong>
      <span>{{ dictionary.collaborationEmptyBody }}</span>
    </div>

    <div v-else class="collaboration-workspace">
      <aside class="collaboration-queue" :aria-label="dictionary.collaborationQueueTitle">
        <div class="collaboration-queue-heading">
          <h2>{{ dictionary.collaborationQueueTitle }}</h2>
          <span>{{ reports.length }}</span>
        </div>
        <div class="collaboration-report-list">
          <button
            v-for="report in reports"
            :key="report.reportId"
            :class="['collaboration-report-item', { active: currentReport?.reportId === report.reportId }]"
            type="button"
            :aria-current="currentReport?.reportId === report.reportId ? 'true' : undefined"
            @click="selectReport(report.reportId)"
          >
            <span class="collaboration-report-item-heading">
              <strong>{{ report.title }}</strong>
              <span :class="['collaboration-status', `status-${String(report.status).toLowerCase()}`]">
                {{ statusLabel(report.status) }}
              </span>
            </span>
            <span class="collaboration-report-item-meta">
              {{ reportTypeLabel(report.reportType) }} · {{ formatDate(report.generatedAt) }}
            </span>
            <span class="collaboration-report-assignee">
              <span class="material-icons" aria-hidden="true">person</span>
              {{ assigneeLabel(report.assignee) }}
            </span>
          </button>
        </div>
      </aside>

      <div v-if="detailLoading" class="collaboration-detail collaboration-state" role="status">
        <strong>{{ dictionary.collaborationDetailLoading }}</strong>
      </div>
      <div v-else-if="!selected" class="collaboration-detail collaboration-state collaboration-state-error" role="alert">
        <strong>{{ dictionary.collaborationLoadError }}</strong>
        <span>{{ operationError }}</span>
      </div>
      <article v-else-if="selected" class="collaboration-detail">
        <header class="collaboration-detail-header">
          <div>
            <div class="collaboration-detail-badges">
              <span :class="['collaboration-status', `status-${String(currentReport.status).toLowerCase()}`]">
                {{ statusLabel(currentReport.status) }}
              </span>
              <span>{{ reportTypeLabel(currentReport.reportType) }}</span>
              <span>{{ scopeLabel(currentReport.scope) }}</span>
            </div>
            <h2>{{ currentReport.title }}</h2>
            <p>{{ dictionary.collaborationGeneratedAt }} {{ formatDate(currentReport.generatedAt) }}</p>
          </div>
          <span v-if="!canCollaborate" class="collaboration-read-only">
            <span class="material-icons" aria-hidden="true">lock</span>
            {{ dictionary.collaborationReadOnly }}
          </span>
        </header>

        <section v-if="canCollaborate && !isTerminal" class="collaboration-controls" :aria-label="dictionary.collaborationControlsTitle">
          <label>
            <span>{{ dictionary.collaborationStatusLabel }}</span>
            <select
              :value="currentReport.status"
              :disabled="Boolean(pendingAction)"
              @change="changeStatus($event.target.value)"
            >
              <option :value="currentReport.status">{{ statusLabel(currentReport.status) }}</option>
              <option v-for="status in availableStatuses" :key="status" :value="status">
                {{ statusLabel(status) }}
              </option>
            </select>
          </label>
          <label>
            <span>{{ dictionary.collaborationAssigneeLabel }}</span>
            <select
              :value="currentReport.assignee?.userId ?? ''"
              :disabled="Boolean(pendingAction)"
              @change="handleAssigneeChange"
            >
              <option value="">{{ dictionary.collaborationUnassigned }}</option>
              <option v-for="assignee in assignees" :key="assignee.userId" :value="assignee.userId">
                {{ assigneeLabel(assignee) }}
              </option>
            </select>
          </label>
        </section>

        <div v-if="isTerminal" class="collaboration-notice">
          <span class="material-icons" aria-hidden="true">task_alt</span>
          <span>{{ dictionary.collaborationTerminalNotice }}</span>
        </div>
        <div v-if="successMessage" class="collaboration-feedback" role="status">{{ successMessage }}</div>
        <div v-if="operationError" class="collaboration-feedback collaboration-feedback-error" role="alert">
          <span>{{ operationError }}</span>
          <button v-if="hasConflict" class="text-button" type="button" @click="reloadConflict">
            {{ dictionary.collaborationReloadConflict }} (v{{ conflictVersion }})
          </button>
        </div>

        <section class="collaboration-report-content">
          <h3>{{ dictionary.collaborationReportContent }}</h3>
          <div class="markdown-body" v-html="reportHtml"></div>
        </section>

        <section class="collaboration-timeline">
          <div class="collaboration-timeline-heading">
            <h3>{{ dictionary.collaborationTimelineTitle }}</h3>
            <span>{{ selected.timeline.length }}</span>
          </div>
          <ol>
            <li v-for="event in selected.timeline" :key="event.id" :class="`event-${String(event.type).toLowerCase()}`">
              <span class="collaboration-timeline-marker" aria-hidden="true"></span>
              <div class="collaboration-timeline-entry">
                <div>
                  <strong>{{ actorLabel(event) }}</strong>
                  <time :datetime="event.createdAt">{{ formatDate(event.createdAt) }}</time>
                </div>
                <p v-if="event.type === 'COMMENT_ADDED'" class="collaboration-comment">{{ event.commentBody }}</p>
                <p v-else>{{ eventLabel(event) }}</p>
              </div>
            </li>
          </ol>

          <form v-if="canCollaborate && !isTerminal" class="collaboration-comment-form" @submit.prevent="handleCommentSubmit">
            <label for="collaboration-comment">{{ dictionary.collaborationCommentLabel }}</label>
            <textarea
              id="collaboration-comment"
              v-model="commentBody"
              maxlength="2000"
              rows="4"
              :placeholder="dictionary.collaborationCommentPlaceholder"
              :disabled="Boolean(pendingAction)"
            ></textarea>
            <div>
              <span>{{ commentBody.length }}/2000</span>
              <button
                class="primary-button"
                type="submit"
                :disabled="!commentBody.trim() || Boolean(pendingAction)"
              >
                {{ pendingAction === "comment" ? dictionary.collaborationCommentPending : dictionary.collaborationAddComment }}
              </button>
            </div>
          </form>
        </section>
      </article>
    </div>
  </section>
</template>
