# Issue tracker: GitHub

Issues and specs live in https://github.com/anilbeesetti/nextplayer/issues.
Use the `gh` CLI with `--repo anilbeesetti/nextplayer` for issue operations.

## Conventions

- Create: `gh issue create --title "..." --body-file <file>`.
- Read: `gh issue view <number> --json number,title,body,labels,comments`.
- List: `gh issue list --state open --json number,title,body,labels,assignees`.
  Add label filters as needed.
- Update: `gh issue edit <number> --body-file <file>`.
- Comment: `gh issue comment <number> --body-file <file>`.
- Label: `gh issue edit <number> --add-label "<label>"` or
  `--remove-label "<label>"`.
- Close: post the explanation as a comment, then run
  `gh issue close <number>`. Post the comment even if already closed.
- Sub-issues: create with `--parent <parent>`, or attach an existing
  issue with `gh issue edit <parent> --add-sub-issue <child>`.
- Dependencies: use native relationships with
  `gh issue edit <child> --add-blocked-by <blocker>`.
  A ticket is startable only when all its blockers are closed.
- Claim: `gh issue edit <number> --add-assignee @me`.

When a skill says "publish to the issue tracker", create a GitHub issue.
When it says "fetch the relevant ticket", read the GitHub issue.

## Pull requests as a triage surface

**PRs as a request surface: no.**

## Wayfinding

The map is an issue labelled `wayfinder:map`.
Tickets are its sub-issues, labelled `wayfinder:<type>`:
`research`, `prototype`, `grilling`, or `task`.

Choose the first open child in map order with no assignee or open blocker.
Claim it before work. On resolution, comment, close it, and append the
finding and ticket link to the map's Decisions-so-far section.
