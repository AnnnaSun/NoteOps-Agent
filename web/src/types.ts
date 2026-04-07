export type ApiEnvelope<T> = {
  success: boolean;
  trace_id: string | null;
  data: T | null;
  error?: {
    code: string;
    message: string;
  } | null;
  meta: {
    server_time: string;
  };
};

export type CaptureResponse = {
  capture_job_id: string;
  source_type: string;
  status: string;
  note_id: string | null;
  failure_reason: string | null;
  analysis_preview: {
    title_candidate: string;
    summary: string;
    key_points: string[];
    tags: string[];
    idea_candidate: string | null;
    confidence: number;
    language: string | null;
    warnings: string[];
  } | null;
  created_at: string;
  updated_at: string;
};

export type NoteSummary = {
  id: string;
  user_id: string;
  title: string;
  current_summary: string;
  current_key_points: string[];
  latest_content_id: string | null;
  updated_at: string;
};

export type NoteDetail = {
  id: string;
  user_id: string;
  title: string;
  current_summary: string;
  current_key_points: string[];
  latest_content_id: string | null;
  latest_content_type: string | null;
  source_uri: string | null;
  raw_text: string | null;
  clean_text: string | null;
  created_at: string;
  updated_at: string;
  evidence_blocks: Array<{
    id: string;
    content_type: string;
    source_uri: string | null;
    source_name: string | null;
    relation_label: string | null;
    summary_snippet: string | null;
    created_at: string;
  }>;
};

export type ReviewTodayItem = {
  id: string;
  note_id: string;
  queue_type: string;
  completion_status: string;
  completion_reason: string | null;
  mastery_score: number | null;
  next_review_at: string | null;
  retry_after_hours: number;
  unfinished_count: number;
  title: string;
  current_summary: string;
  current_key_points: string[];
  ai_recall_summary: string | null;
  ai_review_key_points: string[];
  ai_extension_preview: string | null;
};

export type ReviewPrepResult = {
  review_item_id: string;
  ai_recall_summary: string;
  ai_review_key_points: string[];
  ai_extension_preview: string | null;
};

export type ReviewFeedbackResult = {
  review_item_id: string;
  recall_feedback_summary: string | null;
  next_review_hint: string | null;
  extension_suggestions: string[];
  follow_up_task_suggestion: string | null;
};

export type TaskItem = {
  id: string;
  user_id: string;
  note_id: string | null;
  task_source: string;
  task_type: string;
  title: string;
  description: string | null;
  status: string;
  priority: number;
  due_at: string | null;
  related_entity_type: string;
  related_entity_id: string | null;
  created_at: string;
  updated_at: string;
};

export type WorkspaceToday = {
  today_reviews: ReviewTodayItem[];
  today_tasks: TaskItem[];
};

export type WorkspaceUpcoming = {
  upcoming_reviews: ReviewTodayItem[];
  upcoming_tasks: TaskItem[];
};

export type SearchExactMatch = {
  note_id: string;
  title: string;
  current_summary: string;
  current_key_points: string[];
  latest_content: string;
  updated_at: string;
};

export type SearchRelatedMatch = {
  note_id: string;
  title: string;
  current_summary: string;
  current_key_points: string[];
  latest_content: string;
  relation_reason: string;
  updated_at: string;
  is_ai_enhanced: boolean;
};

export type SearchExternalSupplement = {
  source_name: string;
  source_uri: string;
  summary: string;
  keywords: string[];
  relation_label: string;
  relation_tags: string[];
  summary_snippet: string;
  is_ai_enhanced: boolean;
};

export type SearchResult = {
  query: string;
  exact_matches: SearchExactMatch[];
  related_matches: SearchRelatedMatch[];
  external_supplements: SearchExternalSupplement[];
  ai_enhancement_status: string;
};

export type ReviewCompletionPayload = {
  user_id: string;
  completion_status: string;
  completion_reason?: string;
  self_recall_result?: string;
  note?: string;
};

export type ReviewCompletionResult = {
  id: string;
  note_id: string;
  queue_type: string;
  completion_status: string;
  completion_reason: string | null;
  self_recall_result: string | null;
  note: string | null;
  next_review_at: string | null;
  retry_after_hours: number;
  unfinished_count: number;
  mastery_score: number | null;
  recall_feedback_summary: string | null;
  next_review_hint: string | null;
  extension_suggestions: string[];
  follow_up_task_suggestion: string | null;
};

export type ChangeProposal = {
  id: string;
  user_id: string;
  note_id: string;
  trace_id: string | null;
  proposal_type: string;
  target_layer: string;
  risk_level: string;
  diff_summary: string;
  before_snapshot: Record<string, unknown>;
  after_snapshot: Record<string, unknown>;
  source_refs: Array<Record<string, unknown>>;
  status: string;
  created_at: string;
  updated_at: string;
};

export type SearchEvidenceResult = {
  note_id: string;
  content_id: string;
  content_type: string;
  source_uri: string;
  relation_label: string;
};
