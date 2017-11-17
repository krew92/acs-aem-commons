# ACS AEM Commons

[![Join the chat at https://gitter.im/Adobe-Consulting-Services/acs-aem-commons](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/Adobe-Consulting-Services/acs-aem-commons?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

[![Build Status](https://travis-ci.org/Adobe-Consulting-Services/acs-aem-commons.png)](https://travis-ci.org/Adobe-Consulting-Services/acs-aem-commons)

[![Code Climate](https://codeclimate.com/github/Adobe-Consulting-Services/acs-aem-commons/badges/gpa.svg)](https://codeclimate.com/github/Adobe-Consulting-Services/acs-aem-commons)

This project is a unified collection of AEM/CQ code generated by the AEM consulting practice.

## Building

This project uses Maven for building. Common commands:

From the root directory, run ``mvn -PautoInstallPackage clean install`` to build the bundle and content package and install to a CQ instance.

From the bundle directory, run ``mvn -PautoInstallBundle clean install`` to build *just* the bundle and install to a CQ instance.

## Using with VLT

To use vlt with this project, first build and install the package to your local CQ instance as described above. Then cd to `content/src/main/content/jcr_root` and run

    vlt --credentials admin:admin checkout -f ../META-INF/vault/filter.xml --force http://localhost:4502/crx

Once the working copy is created, you can use the normal ``vlt up`` and ``vlt ci`` commands.

## Specifying CRX Host/Port

The CRX host and port can be specified on the command line with:
mvn -Dcrx.host=otherhost -Dcrx.port=5502 <goals>

## Distribution

Watch this space.

## Rules

* Spaces, not tabs.
* Provide documentation in the parent org GH project: https://github.com/Adobe-Consulting-Services/adobe-consulting-services.github.io
* Target AEM 6.0+. Check the [compatability table](http://adobe-consulting-services.github.io/acs-aem-commons/pages/compatibility.html) for compatibility of older versions.
* API classes and interfaces must have JavaDocs. Not necessary for implementation classes.
* Don't use author tags. This is a community project.

## Want commit rights?

* Create an issue.
