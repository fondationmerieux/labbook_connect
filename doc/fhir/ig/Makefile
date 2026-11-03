# Builds the Implementation Guide. Both steps run in throwaway containers, so nothing has to
# be installed on the workstation. See README.md.
NODE_IMAGE      = docker.io/library/node:20
PUBLISHER_IMAGE = docker.io/hl7fhir/ig-publisher-base:latest
PUBLISHER       = $(HOME)/bin/publisher.jar

# Turns the FSH sources into FHIR resources, under fsh-generated/
sushi:
	podman run --rm -v $(CURDIR):/ig:z -w /ig $(NODE_IMAGE) npx -y fsh-sushi@3 .

# Turns those resources into the HTML site, under output/. It runs in the image the tool is
# published with, which carries everything it needs. -no-sushi because the publisher would
# otherwise run SUSHI again itself.
publish:
	podman run --rm \
		-v $(CURDIR):/ig:z \
		-v $(PUBLISHER):/publisher.jar:ro,z \
		-v $(HOME)/.fhir:/root/.fhir:z \
		$(PUBLISHER_IMAGE) \
		java -jar /publisher.jar -ig /ig -no-sushi
	$(MAKE) --no-print-directory trim

# The banner of every page ends with a link to the directory of published versions of the
# guide. Nothing is published, so that link leads nowhere: only this sentence is taken out,
# the banner itself keeps the version and the build date.
trim:
	sed -i "s| See the <a href='[^']*'>Directory of published versions</a>||" output/*.html

clean:
	rm -rf fsh-generated output temp template input-cache

.PHONY: sushi publish trim clean
