# Paisa Tracker

An offline personal expense tracker. No application code yet — the repo currently holds
only setup files.

- Remote: https://github.com/amteen56/paisa-tracker
- Default branch: `main`

## Git identity — IMPORTANT

This machine is set up for **two GitHub accounts**. This repo belongs to the **personal**
account; everything else on the machine belongs to the **work** account.

| | Personal (this repo) | Work (everything else) |
|---|---|---|
| GitHub user | `amteen56` | `abdulmateeninfigo` |
| Commit name | `AbdulMateen` | `Abdul Mateen` |
| Commit email | `mateena946@gmail.com` | `amateen@devnext.net` |
| Auth | SSH key `~/.ssh/id_github_personal` | HTTPS via Git Credential Manager |
| Remote host | `github-personal` (alias) | `github.com` |

Separation works through two repo-local settings — **never change the global config**:

```bash
git config user.name  "AbdulMateen"          # repo-local
git config user.email "mateena946@gmail.com" # repo-local
git remote set-url origin git@github-personal:amteen56/<repo>.git
```

The **remote alias** decides which account pushes; the **local `user.email`** decides who
commits are attributed to. Both are required — with only the alias, pushes succeed but
commits show up under the work email.

`~/.ssh/config` defines the alias with `IdentitiesOnly yes`, so the personal key is offered
only to remotes that name `github-personal`. Work repos use HTTPS and never touch it.

### Rules for agents working in this repo

- Do **not** run `git config --global ...` — it would leak the personal identity into every
  work repo on this machine.
- Do **not** use `gh` for anything that writes (`gh repo create`, `gh pr create`). The `gh`
  CLI is authenticated as the **work** account and would act under the wrong identity.
  Use plain `git` over the SSH alias instead.
- Before the first commit in a session, verify identity:
  `git config user.email` must print `mateena946@gmail.com`.
- For any *new* personal repo, apply the three commands above — a fresh clone defaults to
  the work identity.

### Gotchas already hit

- `~/.ssh/config` must be saved **without a UTF-8 BOM**. Git for Windows uses its own
  bundled `ssh`, which fails with `Bad configuration option: \357\273\277#` on a BOM.
  PowerShell's `Out-File`/`Set-Content -Encoding utf8` writes one — use the Write tool or a
  bash heredoc instead.
- `git push` writes progress to stderr, so PowerShell renders successful pushes as red
  `NativeCommandError` text. Check for `-> main` and the exit code, not the color.
- The personal SSH key has **no passphrase**, so `ssh-agent` is not needed and stays
  disabled.

## Environment

- Windows 11, PowerShell 5.1 primary shell (no `&&`, no ternary, no `??`)
- Git for Windows with `core.autocrlf=true` — LF → CRLF normalization warnings on commit are
  expected and not a problem
- System `init.defaultBranch` is `master`, so pass `-b main` when initializing new repos to
  match GitHub
