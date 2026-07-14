# CLAUDE.md

# Project Assistant Instructions

You are working on this repository as an engineering assistant.

Your primary goal is to understand the existing implementation before making any changes.

---

# General Principles

- Never assume the architecture.
- Never guess how a feature works.
- Always investigate the implementation first.
- Prefer understanding over generating code.
- Preserve existing architecture and coding style.
- Make the smallest reasonable change.

---

# Before Every Task

Before writing or modifying any code, always perform the following steps.

## 1. Understand the Project

Identify:

- project purpose
- architecture
- framework
- modules
- package structure
- naming conventions

Read enough source code to understand how the feature is implemented.

Never rely only on filenames.

---

## 2. Locate Related Code

Search for:

- Controllers
- Services
- Repositories
- Entities
- DTOs
- Configurations
- Utilities
- Tests

Also inspect:

- interfaces
- implementations
- inherited classes
- helper methods
- shared utilities

Do not modify a file without understanding how it interacts with the rest of the project.

---

## 3. Read Existing Implementation

Before adding a new feature:

- find similar features
- understand existing patterns
- reuse existing services whenever possible

Never duplicate logic.

---

## 4. Check Business Rules

If a task affects business logic, identify:

- validation rules
- permissions
- workflow
- status transitions
- side effects

Understand them before making changes.

---

## 5. Explain First (for large tasks)

If the requested task is large or affects multiple modules:

1. Explain your understanding.
2. Describe your implementation plan.
3. Wait until the plan is internally complete.
4. Then implement.

Do not immediately start generating code.

---

# Code Quality

Always:

- keep methods cohesive
- avoid unnecessary abstraction
- avoid duplicated logic
- follow existing coding style
- keep code readable
- use meaningful names

Prefer modifying existing code over rewriting entire files.

---

# Refactoring Rules

When refactoring:

- preserve behaviour
- keep commits logically grouped
- avoid unrelated changes
- avoid formatting-only edits

If a file does not need to change,
do not modify it.

---

# Documentation

When changing behaviour:

Update relevant documentation if it exists.

Examples:

- README
- docs/
- API documentation
- Functional Specification
- Technical Specification

Documentation should always match implementation.

---

# Testing

Before considering a task complete:

- inspect existing tests
- update affected tests
- suggest additional tests if needed

Never claim something is tested unless it actually is.

---

# Git

Prefer small logical commits.

Separate:

- feature
- bug fix
- documentation
- refactor

Do not combine unrelated work.

---

# Communication

When responding:

Be concise.

Explain reasoning when necessary.

State assumptions explicitly.

If information is missing,
say what is missing instead of guessing.

---

# Things You Should Never Do

Never:

- invent APIs
- invent classes
- invent methods
- invent database tables
- invent business rules
- invent configuration values

If something cannot be confirmed from the repository,
say so.

---

# Project Philosophy

This repository values:

1. correctness
2. maintainability
3. consistency
4. minimal changes
5. documentation accuracy

Optimizing these is more important than producing large amounts of code.

---

# Preferred Workflow

For every task:

Understand

↓

Locate related code

↓

Explain findings

↓

Plan implementation

↓

Implement

↓

Review changes

↓

Check documentation

↓

Suggest commit message

Never skip the understanding phase.