# ==============================================================================
# ⚙️ Galasa Ecosystem - Engine Controller Container Image (Pass 2 Layered Build)
# ==============================================================================
FROM galasadev/galasa-boot-embedded-amd64:0.19.1

LABEL name="galasa-engine-controller" \
      vendor="Galasa Ecosystem" \
      description="Galasa Engine Controller microservice container (Layered workspace build)"

WORKDIR /galasa

# Copy locally-built OSGi JARs on top of base image
COPY build/libs/ /galasa/
