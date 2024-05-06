# .git hooks

Copy the hooks directly into `.git/hooks`.

## Why are not .git/hooks included by default?
- Automatically executing scripts that can be pushed to a repository could be a significant security risk.
- Developers might need to customize hooks based on their local development environment, which could conflict with a one-size-fits-all approach in a shared repository.
- Alternatives would require you to adjust git config, which would be more work than just copy pasting a file.