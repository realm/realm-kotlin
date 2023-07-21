#!/bin/bash -ex

# Assume Dokka has been run
pushd "`dirname "$0"`"/../docs/html

# Make the output SEO friendly by converting the "h2" title to the proper "h1".
# For Dokka, this is only an issue on the index page (apparently).
sed -i -e 's|<h2\(.*\)</h2>|<h1\1</h1>|' index.html
find . -iname "*.html-e" | xargs rm

popd
