# Github Actions Readme

This file contains information about how we integrate with Github Actions and what to watch out for when editing these files.


## File Structure and Naming

All Github Action related files are located in: `.github/`.

This folder contains two sub-folders:
  
  - `.github/actions`: This folder contains [custom actions](https://docs.github.com/en/actions/creating-actions/about-custom-actions)
    that are called from our workflows.

  - `./github/workflows`: This folder contains all the yaml files that control our workflows. Files with an `include-` prefix 
    are [reusable workflows](https://docs.github.com/en/actions/using-workflows/reusing-workflows) and are only meant to be 
    included as part of other workflows. 


The primary entry point for builds are: `./github/workflows/pr.yml`


## Structuring pipelines

- Github Actions will skip a job if any job dependency was skipped. This includes transitive dependencies. This is the reason 
  why you can see most jobs having a construct like `if: always() && !cancelled() && !contains(needs.*.result, 'failure') 
  && !contains(needs.*.result, 'cancelled')` which work around the issue.


## How to clear all action caches?

Currently, the Github UI and API only allow deleting each cache invidiually. This following shell command will run through all caches and delete each one individually. It requires the Github CLI installed and authenticated as well as `jq`:

```
gh api -H 'Accept: application/vnd.github+json' /repos/realm/realm-kotlin/actions/caches --paginate | jq -r '.actions_caches | .[].id' | xargs -I {} sh -c 'gh api --method DELETE -H "Accept: application/vnd.github+json" /repos/realm/realm-kotlin/actions/caches/{} --silent'
```

of if you have the Github Actions CLI 2.42.0 or later from Homebrew:

```
gh cache delete -a --repo realm/realm-kotlin
```


## See all caches

Access all Github Action caches using: https://github.com/realm/realm-kotlin/actions/caches?query=sort%3Asize-desc