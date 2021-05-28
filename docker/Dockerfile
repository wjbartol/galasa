ARG gradleVersion
ARG dockerRepository
ARG branch
FROM ${dockerRepository}/galasadev/galasa-${branch}-maven-gradle:${gradleVersion}

ARG gitHash

#RUN sed -i "s/--branchname--/${branch}/"    /usr/local/apache2/conf/httpd.conf
#RUN sed -i 's/--repositoryname--/gradle/'  /usr/local/apache2/conf/httpd.conf

COPY repo/ /usr/local/apache2/htdocs/
RUN echo -n ${gitHash} > /usr/local/apache2/htdocs/maven.githash
