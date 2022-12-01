# Github Actions Readme

This file contains information about how we integrate with Github Actions and what to watch out for when editing these files.

## File Structure and Naming

TODO

## Structuring pipelines

- Github Actions will skip a job if any job dependency was skipped. This includes transitive dependencies. This is the reason why you can see 
  most jobs having a construct like `if: always() && (needs.job.result == 'success' || needs.job.result == 'skipped'` which work around the issue.


## How to clear all action caches?

Currently, the Github UI and API only only deleting each cache invidiually. This following shell command will run through all caches and delete each one individually. It requires the Github CLI installed and authenticated as well as `jq`:

```
gh api -H 'Accept: application/vnd.github+json' /repos/realm/realm-kotlin/actions/caches --paginate | jq -r '.actions_caches | .[].key' | sed -e 's/|/%7c/g' -e 's/\[/%5b/g' -e 's/\]/%5d/g' | xargs -I {} sh -c 'gh api --method DELETE -H "Accept: application/vnd.github+json" /repos/realm/realm-kotlin/actions/caches?key={} --silent'
```