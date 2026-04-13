-- Milestones: trackable phases of a project
CREATE TABLE milestones (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id      UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    title           VARCHAR(500) NOT NULL,
    description     TEXT,
    amount          INT NOT NULL DEFAULT 0,
    status          VARCHAR(50) NOT NULL DEFAULT 'pending',
    sort_order      INT NOT NULL DEFAULT 0,
    due_date        DATE,
    completed_date  DATE,
    paid_date       DATE,
    note            TEXT,
    inserted_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_milestones_project_id ON milestones(project_id);
CREATE INDEX idx_milestones_status ON milestones(status);

-- Expenses: cost tracking linked to projects and optionally milestones
CREATE TABLE expenses (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id      UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    milestone_id    UUID REFERENCES milestones(id) ON DELETE SET NULL,
    description     VARCHAR(500) NOT NULL,
    amount          INT NOT NULL,
    category        VARCHAR(100),
    date            DATE NOT NULL DEFAULT CURRENT_DATE,
    vendor          VARCHAR(255),
    inserted_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_expenses_project_id ON expenses(project_id);
CREATE INDEX idx_expenses_milestone_id ON expenses(milestone_id);
CREATE INDEX idx_expenses_category ON expenses(category);

-- Add category to projects
ALTER TABLE projects ADD COLUMN category VARCHAR(100);
