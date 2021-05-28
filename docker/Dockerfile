ARG gradleVersion
ARG dockerRepository
ARG branch
FROM ${dockerRepository}/galasadev/galasa-maven-gradle:${gradleVersion}-${branch}

ARG gitHash

#RUN sed -i "s/--branchname--/${branch}/"    /usr/local/apache2/conf/httpd.conf
#RUN sed -i 's/--repositoryname--/gradle/'  /usr/local/apache2/conf/httpd.conf

COPY repo/ /usr/local/apache2/htdocs/
RUN echo -n ${gitHash} > /usr/local/apache2/htdocs/maven.githash
