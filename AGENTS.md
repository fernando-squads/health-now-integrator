# Git Commit Guidelines

All Git commit messages must be written in English and must use Gitmoji.

Use the following format:

```text
:gitmoji: concise commit message in English
```

Examples:

```text
✨ Add PostgreSQL JDBC dependency
🐛 Fix database connection timeout handling
📝 Update project setup documentation
♻️ Refactor integration configuration
```

Keep commit messages concise, descriptive, and focused on a single change.

# Local Skills

Project-specific skills are located in the `.agents/` directory. Each skill is stored in its own folder and is defined by a `SKILL.md` file.

Before starting work that matches a local skill's scope, read its `SKILL.md` and follow the applicable guidance. Current local skills include:

- `.agents/java-senior-architect/SKILL.md` for Java 8 architecture, analysis, implementation, and code review.
- `.agents/postgres-senior-dba/SKILL.md` for PostgreSQL design, SQL, migrations, performance, security, and operations.
- `.agents/qa-senior/SKILL.md` for test strategy, quality assurance, defect reporting, and release readiness.
