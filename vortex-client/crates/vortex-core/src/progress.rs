//! Progress reporting shared by every long-running operation.

use std::sync::Arc;

#[derive(Debug, Clone)]
pub struct Progress {
    /// Human readable stage, e.g. "Downloading assets".
    pub stage: String,
    pub done: u64,
    pub total: u64,
}

impl Progress {
    pub fn fraction(&self) -> f32 {
        if self.total == 0 {
            0.0
        } else {
            (self.done as f32 / self.total as f32).clamp(0.0, 1.0)
        }
    }
}

/// Callback invoked from worker tasks; must be cheap and thread-safe.
pub type ProgressFn = Arc<dyn Fn(Progress) + Send + Sync>;

/// A progress callback that ignores everything.
pub fn silent() -> ProgressFn {
    Arc::new(|_| {})
}

pub(crate) fn report(progress: &ProgressFn, stage: &str, done: u64, total: u64) {
    progress(Progress {
        stage: stage.to_string(),
        done,
        total,
    });
}
