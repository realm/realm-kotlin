#!/bin/bash -ex

usage() {
cat <<EOF
Usage: $0 <root-dokka-dir>
EOF
}

if [ "$#" -ne 1 ] ; then
  usage
  exit 1
fi

# Assume Dokka has been run
pushd $1

# Make the output SEO friendly by converting the "h2" title to the proper "h1".
# For Dokka, this is only an issue on the index page (apparently).
sed -i -e 's|<h2\(.*\)</h2>|<h1\1</h1>|' index.html
find . -iname "*.html-e" | xargs rm

popd
