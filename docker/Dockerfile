ARG baseVersion
ARG dockerRepository
FROM ${dockerRepository}/galasadev/galasa-maven-base:${baseVersion}

ARG branch

RUN sed -i "s/--branchname--/${branch}/"    /usr/local/apache2/conf/httpd.conf
RUN sed -i 's/--repositoryname--/gradle/'  /usr/local/apache2/conf/httpd.conf

COPY repo/ /usr/local/apache2/htdocs/
COPY gradle.githash /usr/local/apache2/htdocs/gradle.githash
