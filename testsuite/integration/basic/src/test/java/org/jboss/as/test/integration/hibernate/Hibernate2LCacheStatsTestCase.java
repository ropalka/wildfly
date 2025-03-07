/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.hibernate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.sql.Connection;
import java.util.HashSet;
import java.util.Set;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.hibernate.stat.Statistics;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.test.shared.util.AssumeTestGroupUtil;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test that Hibernate statistics is working on native Hibernate and second level cache
 *
 * @author Madhumita Sadhukhan
 */
@RunWith(Arquillian.class)
public class Hibernate2LCacheStatsTestCase {

    private static final String FACTORY_CLASS = "<property name=\"hibernate.cache.region.factory_class\">org.infinispan.hibernate.cache.v51.InfinispanRegionFactory</property>";
    private static final String MODULE_DEPENDENCIES = "Dependencies: org.hibernate.envers export,org.hibernate\n";

    private static final String ARCHIVE_NAME = "hibernateSecondLevelStats_test";

    public static final String hibernate_cfg = "<?xml version='1.0' encoding='utf-8'?>"
            + "<!DOCTYPE hibernate-configuration PUBLIC " + "\"//Hibernate/Hibernate Configuration DTD 3.0//EN\" "
            + "\"http://www.hibernate.org/dtd/hibernate-configuration-3.0.dtd\">"
            + "<hibernate-configuration><session-factory>" + "<property name=\"show_sql\">false</property>"
            + "<property name=\"hibernate.cache.use_second_level_cache\">true</property>"
            + "<property name=\"hibernate.show_sql\">false</property>" + FACTORY_CLASS
            + "<property name=\"hibernate.cache.infinispan.shared\">false</property>"
            + "<mapping resource=\"testmapping.hbm.xml\"/>" + "</session-factory></hibernate-configuration>";

    public static final String testmapping = "<?xml version=\"1.0\"?>" + "<!DOCTYPE hibernate-mapping PUBLIC "
            + "\"-//Hibernate/Hibernate Mapping DTD 3.0//EN\" " + "\"http://www.hibernate.org/dtd/hibernate-mapping-3.0.dtd\">"
            + "<hibernate-mapping package=\"org.jboss.as.test.integration.hibernate\">"
            + "<class name=\"org.jboss.as.test.integration.hibernate.Planet\" table=\"PLANET\">"
            + "<cache usage=\"transactional\"/>" + "<id name=\"planetId\" column=\"planetId\">"
            + "<generator class=\"native\"/>" + "</id>" + "<property name=\"planetName\" column=\"planet_name\"/>"
            + "<property name=\"galaxy\" column=\"galaxy_name\"/>" + "<property name=\"star\" column=\"star_name\"/>"
            + "<set name=\"satellites\">" + "<cache usage=\"read-only\"/>" + "<key column=\"id\"/>"
            + "<one-to-many class=\"org.jboss.as.test.integration.hibernate.Satellite\"/>" + "</set>" + "</class>"
            + "<class name=\"org.jboss.as.test.integration.hibernate.Satellite\" table=\"SATELLITE\">" + "<id name=\"id\">"
            + "<generator class=\"native\"/>" + "</id>" + "<property name=\"name\" column=\"satellite_name\"/>"
            + "</class></hibernate-mapping>";

    @ArquillianResource
    private static InitialContext iniCtx;

    @BeforeClass
    public static void beforeClass() throws NamingException {
        // TODO This can be re-looked at once HHH-13188 is resolved. This may require further changes in Hibernate.
        AssumeTestGroupUtil.assumeSecurityManagerDisabled();

        iniCtx = new InitialContext();
    }

    @Deployment
    public static Archive<?> deploy() throws Exception {

        EnterpriseArchive ear = ShrinkWrap.create(EnterpriseArchive.class, ARCHIVE_NAME + ".ear");
        // add required jars as manifest dependencies
        ear.addAsManifestResource(new StringAsset(MODULE_DEPENDENCIES), "MANIFEST.MF");

        JavaArchive lib = ShrinkWrap.create(JavaArchive.class, "beans.jar");
        lib.addClasses(SFSBHibernate2LcacheStats.class);
        ear.addAsModule(lib);

        lib = ShrinkWrap.create(JavaArchive.class, "entities.jar");
        lib.addClasses(Planet.class);
        lib.addClasses(Satellite.class);
        lib.addAsResource(new StringAsset(testmapping), "testmapping.hbm.xml");
        lib.addAsResource(new StringAsset(hibernate_cfg), "hibernate.cfg.xml");
        ear.addAsLibraries(lib);

        final WebArchive main = ShrinkWrap.create(WebArchive.class, "main.war");
        main.addClasses(Hibernate2LCacheStatsTestCase.class);
        ear.addAsModule(main);

        // add application dependency on H2 JDBC driver, so that the Hibernate classloader (same as app classloader)
        // will see the H2 JDBC driver.
        // equivalent hack for use of shared Hiberante module, would be to add the H2 dependency directly to the
        // shared Hibernate module.
        // also add dependency on org.slf4j
        ear.addAsManifestResource(new StringAsset("<jboss-deployment-structure>" + " <deployment>" + " <dependencies>"
                + " <module name=\"com.h2database.h2\" />" + " <module name=\"org.slf4j\"/>" + " </dependencies>"
                + " </deployment>" + "</jboss-deployment-structure>"), "jboss-deployment-structure.xml");

        return ear;
    }

    protected static <T> T lookup(String beanName, Class<T> interfaceType) throws NamingException {
        try {
            return interfaceType.cast(iniCtx.lookup("java:global/" + ARCHIVE_NAME + "/" + "beans/" + beanName + "!"
                    + interfaceType.getName()));
        } catch (NamingException e) {
            throw e;
        }
    }

    protected <T> T rawLookup(String name, Class<T> interfaceType) throws NamingException {
        return interfaceType.cast(iniCtx.lookup(name));
    }

    @Test
    public void testHibernateStatistics() throws Exception {
        SFSBHibernate2LcacheStats sfsb = lookup("SFSBHibernate2LcacheStats", SFSBHibernate2LcacheStats.class);
        // setup Configuration and SessionFactory
        sfsb.setupConfig();
        try {
            Set<Satellite> satellites1 = new HashSet<Satellite>();
            Satellite sat = new Satellite();
            sat.setName("MOON");
            satellites1.add(sat);

            Planet s1 = sfsb.prepareData("EARTH", "MILKY WAY", "SUN", satellites1);
            Planet s2 = sfsb.getPlanet(s1.getPlanetId());

            DataSource ds = rawLookup("java:jboss/datasources/ExampleDS", DataSource.class);
            Connection conn = ds.getConnection();

            int updated = conn.prepareStatement("update PLANET set galaxy_name='ANDROMEDA' where planetId=1").executeUpdate();
            assertTrue("was able to update added Planet.  update count=" + updated, updated > 0);
            conn.close();
            // read updated (dirty) data from second level cache
            s2 = sfsb.getPlanet(s2.getPlanetId());
            assertTrue("was able to read updated Planet entity", s2 != null);
            assertEquals("Galaxy for Planet " + s2.getPlanetName() + " was read from second level cache = " + s2.getGalaxy(),
                    "MILKY WAY", s2.getGalaxy());

            assertEquals(s2.getSatellites().size(), 1);

            Statistics stats = sfsb.getStatistics();

            assertEquals(stats.getCollectionLoadCount(), 1);
            assertEquals(stats.getEntityLoadCount(), 2);
            assertEquals(stats.getSecondLevelCacheHitCount(), 1);
            // Collection in secondlevel cache before eviction
            assertTrue(sfsb.isSatellitesPresentInCache(1));

            Statistics statsAfterEviction = sfsb.getStatisticsAfterEviction();
            // Collection in secondlevel cache after eviction
            assertFalse(sfsb.isSatellitesPresentInCache(1));
        } finally {
            sfsb.cleanup();
        }

    }
}
