/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.cps.postgresql.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import dev.galasa.extensions.common.postgresql.PostgresqlConnectionPoolManager;
import dev.galasa.framework.spi.ConfigurationPropertyStoreException;

/**
 * Integration tests for {@link PostgresqlConfigurationPropertyStore} using H2 in-memory database.
 *
 * Every test gets a fresh H2 database — isolation is guaranteed by the unique DB name per test method.
 */
public class PostgresqlCpsTest {

    private PostgresqlConnectionPoolManager poolManager;
    private PostgresqlConfigurationPropertyStore cps;

    @Before
    public void setUp() throws Exception {
        // H2 in-memory, unique DB per test run, MySQL compat mode for MERGE support
        String jdbcUrl = "jdbc:h2:mem:cps_test_" + System.nanoTime()
                + ";DB_CLOSE_DELAY=-1;MODE=MySQL";
        poolManager = new PostgresqlConnectionPoolManager(jdbcUrl, "sa", "");
        poolManager.initializePool();
        cps = new PostgresqlConfigurationPropertyStore(poolManager);
    }

    @After
    public void tearDown() {
        if (poolManager != null) {
            poolManager.close();
        }
    }

    // ─── Basic set / get ──────────────────────────────────────────────────────

    @Test
    public void testSetAndGetProperty() throws ConfigurationPropertyStoreException {
        cps.setProperty("framework.hub.url", "http://localhost:8080");

        String val = cps.getProperty("framework.hub", "url");
        assertThat(val).isEqualTo("http://localhost:8080");
    }

    @Test
    public void testGetPropertyReturnsNullWhenAbsent() throws ConfigurationPropertyStoreException {
        assertThat(cps.getProperty("framework.nonexistent", "key")).isNull();
    }

    @Test
    public void testSetPropertyNullNameThrows() {
        assertThatThrownBy(() -> cps.setProperty(null, "value"))
                .isInstanceOf(ConfigurationPropertyStoreException.class);
    }

    @Test
    public void testSetPropertyNullValueThrows() {
        assertThatThrownBy(() -> cps.setProperty("key", null))
                .isInstanceOf(ConfigurationPropertyStoreException.class);
    }

    // ─── Upsert (overwrite existing) ─────────────────────────────────────────

    @Test
    public void testSetPropertyOverwritesExisting() throws ConfigurationPropertyStoreException {
        cps.setProperty("framework.hub.url", "http://old");
        cps.setProperty("framework.hub.url", "http://new");

        assertThat(cps.getProperty("framework.hub", "url")).isEqualTo("http://new");
        assertThat(cps.getAllProperties()).hasSize(1);
    }

    // ─── Infix / variant resolution ───────────────────────────────────────────

    @Test
    public void testInfixesPropertyVariants() throws ConfigurationPropertyStoreException {
        cps.setProperty("zos.image.credentialid", "DEFAULT_CREDS");
        cps.setProperty("zos.image.PLEXMA.MVMA.credentialid", "SPECIFIC_CREDS");

        String specific = cps.getProperty("zos.image", "credentialid", "PLEXMA", "MVMA");
        assertThat(specific).isEqualTo("SPECIFIC_CREDS");

        cps.deleteProperty("zos.image.PLEXMA.MVMA.credentialid");
        String fallback = cps.getProperty("zos.image", "credentialid", "PLEXMA", "MVMA");
        assertThat(fallback).isEqualTo("DEFAULT_CREDS");
    }

    // ─── Batch set / delete ───────────────────────────────────────────────────

    @Test
    public void testPrefixedAndDeleteOperations() throws ConfigurationPropertyStoreException {
        cps.setProperties(Map.of(
            "test.stream.1.name", "Stream1",
            "test.stream.2.name", "Stream2",
            "other.prop", "Value"
        ));

        Map<String, String> prefixed = cps.getPrefixedProperties("test.stream.");
        assertThat(prefixed).hasSize(2);

        cps.deletePrefixedProperties("test.stream.");
        assertThat(cps.getPrefixedProperties("test.stream.")).isEmpty();
        assertThat(cps.getAllProperties()).hasSize(1);
    }

    @Test
    public void testSetPropertiesNullNoOp() throws ConfigurationPropertyStoreException {
        cps.setProperties(null);  // should not throw
        assertThat(cps.getAllProperties()).isEmpty();
    }

    // ─── Prefix boundary isolation ────────────────────────────────────────────

    @Test
    public void testPrefixBoundaryIsolation() throws ConfigurationPropertyStoreException {
        cps.setProperties(Map.of(
            "dev.galasa.cps.foo",        "val1",
            "dev.galasa.cps_extra.bar",  "val2",
            "dev.galasa.cps.baz",        "val3"
        ));

        Map<String, String> matched = cps.getPrefixedProperties("dev.galasa.cps.");
        assertThat(matched).hasSize(2);
        assertThat(matched.keySet())
                .containsExactlyInAnyOrder("dev.galasa.cps.foo", "dev.galasa.cps.baz");
    }

    // ─── Unicode ──────────────────────────────────────────────────────────────

    @Test
    public void testUnicodePropertyKeysAndValues() throws ConfigurationPropertyStoreException {
        cps.setProperty("framework.i18n.greeting", "こんにちは世界 🌍 🚀");
        cps.setProperty("framework.i18n.über", "spätes_Mädchen_123");

        assertThat(cps.getProperty("framework.i18n", "greeting"))
                .isEqualTo("こんにちは世界 🌍 🚀");
        assertThat(cps.getProperty("framework.i18n", "über"))
                .isEqualTo("spätes_Mädchen_123");
    }

    // ─── Empty value ──────────────────────────────────────────────────────────

    @Test
    public void testAbsentAndEmptyValueHandling() throws ConfigurationPropertyStoreException {
        cps.setProperty("framework.empty.key", "");

        assertThat(cps.getProperty("framework.empty", "key")).isEqualTo("");
        assertThat(cps.getProperty("framework.nonexistent", "key")).isNull();
    }

    // ─── Namespaces ───────────────────────────────────────────────────────────

    @Test
    public void testGetCPSNamespaces() throws ConfigurationPropertyStoreException {
        cps.setProperties(Map.of(
            "cps.namespace1.propA", "1",
            "cps.namespace1.propB", "2",
            "cps.namespace2.propC", "3",
            "standalone",           "4"
        ));

        var namespaces = cps.getCPSNamespaces();
        assertThat(namespaces).contains("cps", "standalone");
    }

    // ─── Persistence across operations ───────────────────────────────────────

    @Test
    public void testPersistenceAcrossOperations() throws ConfigurationPropertyStoreException {
        cps.setProperty("persistent.key", "original");
        assertThat(cps.getProperty("persistent", "key")).isEqualTo("original");

        cps.setProperty("persistent.key", "updated");
        assertThat(cps.getProperty("persistent", "key")).isEqualTo("updated");

        cps.deleteProperty("persistent.key");
        assertThat(cps.getProperty("persistent", "key")).isNull();
    }

    // ─── getAllProperties ─────────────────────────────────────────────────────

    @Test
    public void testGetAllProperties() throws ConfigurationPropertyStoreException {
        cps.setProperties(Map.of(
            "a.b.c", "1",
            "x.y.z", "2"
        ));

        Map<String, String> all = cps.getAllProperties();
        assertThat(all).containsEntry("a.b.c", "1").containsEntry("x.y.z", "2");
    }

    // ─── deleteProperty idempotent ────────────────────────────────────────────

    @Test
    public void testDeletePropertyIdempotent() throws ConfigurationPropertyStoreException {
        cps.setProperty("key.to.delete", "val");
        cps.deleteProperty("key.to.delete");
        cps.deleteProperty("key.to.delete"); // second delete must not throw
        assertThat(cps.getAllProperties()).isEmpty();
    }

    // ─── deletePrefixedProperties on empty prefix ─────────────────────────────

    @Test
    public void testDeletePrefixedPropertiesNullNoOp() throws ConfigurationPropertyStoreException {
        cps.setProperty("keep.me", "val");
        cps.deletePrefixedProperties(null); // must not throw
        assertThat(cps.getAllProperties()).hasSize(1);
    }
}
