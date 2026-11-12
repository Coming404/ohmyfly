package catalog

const resultSchema = `
CREATE TABLE flight_tasks (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 mode TEXT NOT NULL CHECK(mode IN ('Latency','Download','Full')),
 settings TEXT NOT NULL CHECK(json_valid(settings)),
 status TEXT NOT NULL CHECK(status IN ('queued','running','succeeded','failed','cancelled','interrupted')),
 created_at INTEGER NOT NULL, started_at INTEGER, finished_at INTEGER,
 reason TEXT NOT NULL DEFAULT ''
);
CREATE TABLE node_runs (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 task_id INTEGER NOT NULL REFERENCES flight_tasks(id) ON DELETE CASCADE,
 node_id INTEGER NOT NULL REFERENCES nodes(id) ON DELETE CASCADE,
 status TEXT NOT NULL CHECK(status IN ('queued','running','succeeded','failed','cancelled','interrupted')),
 started_at INTEGER, finished_at INTEGER, reason TEXT NOT NULL DEFAULT ''
);
CREATE INDEX node_runs_history ON node_runs(node_id,id DESC);
CREATE INDEX node_runs_task ON node_runs(task_id,id);
CREATE TABLE metric_results (
 run_id INTEGER NOT NULL REFERENCES node_runs(id) ON DELETE CASCADE,
 kind TEXT NOT NULL CHECK(kind IN ('latency','download','upload')),
 status TEXT NOT NULL CHECK(status IN ('running','succeeded','failed','cancelled','interrupted')),
 value REAL, measured_at INTEGER, reason TEXT NOT NULL DEFAULT '',
 PRIMARY KEY(run_id,kind),
 CHECK((status='succeeded' AND value IS NOT NULL AND value>=0 AND measured_at IS NOT NULL)
 OR (status<>'succeeded' AND value IS NULL)),
 CHECK((status='running' AND measured_at IS NULL) OR (status<>'running' AND measured_at IS NOT NULL))
);
CREATE TABLE platform_results (
 run_id INTEGER NOT NULL REFERENCES node_runs(id) ON DELETE CASCADE,
 platform TEXT NOT NULL,
 status TEXT NOT NULL CHECK(status IN ('running','unlocked','locked','failed','cancelled','interrupted')),
 region TEXT NOT NULL DEFAULT '', checked_at INTEGER, reason TEXT NOT NULL DEFAULT '',
 PRIMARY KEY(run_id,platform),
 CHECK((status='running' AND checked_at IS NULL) OR (status<>'running' AND checked_at IS NOT NULL))
);
`
