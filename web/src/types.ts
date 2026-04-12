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

export type IdeaAssessmentResult = {
  problem_statement: string | null;
  target_user: string | null;
  core_hypothesis: string | null;
  mvp_validation_path: string[];
  next_actions: string[];
  risks: string[];
  reasoning_summary: string | null;
};

export type IdeaSummary = {
  id: string;
  user_id: string;
  source_mode: string;
  source_note_id: string | null;
  title: string;
  status: string;
  updated_at: string;
};

export type IdeaDetail = {
  id: string;
  user_id: string;
  source_mode: string;
  source_note_id: string | null;
  title: string;
  raw_description: string | null;
  status: string;
  assessment_result: IdeaAssessmentResult;
  created_at: string;
  updated_at: string;
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

export type IdeaTaskGenerationResult = {
  idea_id: string;
  status: string;
  generated_tasks: TaskItem[];
};

export type TrendAnalysisPayload = {
  summary?: string | null;
  why_it_matters?: string | null;
  topic_tags?: string[];
  signal_type?: string | null;
  note_worthy?: boolean;
  idea_worthy?: boolean;
  suggested_action?: string | null;
  reasoning_summary?: string | null;
};

export type TrendInboxItem = {
  trend_item_id: string;
  user_id: string;
  source_type: string;
  source_item_key: string;
  title: string;
  url: string;
  summary: string;
  normalized_score: number;
  status: string;
  suggested_action: string | null;
  analysis_payload: TrendAnalysisPayload;
  source_published_at: string | null;
  last_ingested_at: string | null;
  updated_at: string;
};

export type TrendActionRequest = {
  user_id: string;
  action: string;
  operator_note?: string | null;
};

export type TrendActionResponse = {
  trace_id: string | null;
  trend_item_id: string;
  action_result: string;
  converted_note_id: string | null;
  converted_idea_id: string | null;
};
