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
  id: string;
  user_id: string;
  input_type: string;
  source_uri: string | null;
  raw_input: string;
  status: string;
  error_code: string | null;
  error_message: string | null;
  created_at: string;
  updated_at: string;
  note_id: string | null;
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
