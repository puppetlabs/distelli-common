PACKAGE_NAME=distelli-common-pom
SHELL := /bin/bash
.SILENT:
.PHONY: has-distelli-config git-has-pushed git-is-clean
all:
	mvn -q -U dependency:build-classpath compile -DincludeScope=runtime -Dmdep.outputFile=target/.classpath -Dmaven.compiler.debug=false

install: has-distelli-config
	. ~/.distelli.config && mvn -q install

test: has-distelli-config
	. ~/.distelli.config && mvn -q -Dsurefire.useFile=false test

clean:
	mvn -q clean

package: has-distelli-config
	. ~/.distelli.config && mvn -q -DincludeScope=runtime dependency:copy-dependencies package

show-deps:
	mvn dependency:tree

has-distelli-config:
	if ! [ -e ~/.distelli.config ]; then \
		echo 'Please create ~/.distelli.config with this content:' 1>&2; \
		echo 'export S3_ENDPOINT="ddb://us-east-1"' 1>&2; \
		echo 'export S3_BUCKET="distelli-unit-test"' 1>&2; \
		echo 'export S3_CREDS="<KEY_ID>=<SECRET_KEY>"' 1>&2; \
		false; \
	fi

#git-has-pushed:
#	! git diff --stat HEAD origin/master | grep . >/dev/null && [ 0 == $${PIPESTATUS[0]} ]

git-is-clean:
	git diff-index --quiet HEAD --

git-is-master:
	[ master = "$$(git rev-parse --abbrev-ref HEAD)" ]

publish: has-distelli-config git-is-clean git-is-master
	if [ -z "$(NEW_VERSION)" ]; then echo 'Please run `make publish NEW_VERSION=1.1`' 1>&2; false; fi
	. ~/.distelli.config && \
		mvn versions:set -DnewVersion=$(NEW_VERSION) && \
		git commit -am '[skip ci][release:prepare] prepare release $(PACKAGE_NAME)-$(NEW_VERSION)' && \
		git tag -m 'Preparing new release $(PACKAGE_NAME)-$(NEW_VERSION)' -a '$(PACKAGE_NAME)-$(NEW_VERSION)' && \
		mvn clean test deploy && \
		mvn versions:set -DnewVersion=$$(echo $(NEW_VERSION) | awk -F. '{OFS=".";$$NF=$$(NF)+1;print $$0}')-SNAPSHOT && \
		git commit -am '[skip ci][release:perform] prepare for next development iteration' && \
		git push --follow-tags

