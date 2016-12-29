PACKAGE_NAME=persistence-pom
SHELL := /bin/bash
.SILENT:
.PHONY: has-distelli-config git-has-pushed git-is-clean
all:
	mvn -q -U dependency:build-classpath compile -DincludeScope=runtime -Dmdep.outputFile=target/.classpath -Dmaven.compiler.debug=false

install: has-distelli-config
	. ~/.distelli.config && mvn -q install

# ~/.distelli.config
# Should contain:
# export MYSQL_ENDPOINT="mysql://<DBENDPOINT>"
# export MYSQL_CREDS="<USERNAME>=<PASSWD>"
# export DDB_ENDPOINT="ddb://us-east-1"
# export DDB_CREDS="<KEY_ID>=<SECRET_KEY>"
# ...totally insecure test key (used for encrypting in the test DB):
# export DB_KEY="AQIDBAUGBwgJCgsMDQ4PEA=="

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
		echo 'export MYSQL_ENDPOINT="mysql://<DBENDPOINT>"' 1>&2; \
		echo 'export MYSQL_CREDS="<USERNAME>=<PASSWD>"' 1>&2; \
		echo 'export DDB_ENDPOINT="ddb://us-east-1"' 1>&2; \
		echo 'export DDB_CREDS="<KEY_ID>=<SECRET_KEY>"' 1>&2; \
		echo 'export DB_KEY="<TEST_KEY>"' 1>&2; \
		echo 'export KMS_ENDPOINT="kms://us-east-1"' 1>&2; \
		echo 'export KMS_CREDS="$$DDB_CREDS"' 1>&2; \
		echo 'export KMS_KEY="$$(aws kms encrypt --key-id arn:aws:kms:... --plaintext fileb://test-key.aes)"' 1>&2; \
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

