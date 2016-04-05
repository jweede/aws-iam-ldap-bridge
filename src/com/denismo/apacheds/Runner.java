/*
 * Copyright (c) 2014 Denis Mikhalkin.
 *
 * This software is provided to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.  You may obtain a copy of the
 * License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.denismo.apacheds;

import com.denismo.apacheds.auth.AWSIAMAuthenticator;
import org.apache.directory.api.ldap.model.constants.SchemaConstants;
import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.name.Rdn;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.api.ldap.model.schema.registries.SchemaLoader;
import org.apache.directory.api.ldap.schema.extractor.SchemaLdifExtractor;
import org.apache.directory.api.ldap.schema.extractor.impl.DefaultSchemaLdifExtractor;
import org.apache.directory.api.ldap.schema.loader.LdifSchemaLoader;
import org.apache.directory.api.ldap.schema.manager.impl.DefaultSchemaManager;
import org.apache.directory.api.util.exception.Exceptions;
import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.DefaultDirectoryService;
import org.apache.directory.server.core.api.CacheService;
import org.apache.directory.server.core.api.CoreSession;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.InstanceLayout;
import org.apache.directory.server.core.api.interceptor.context.AddOperationContext;
import org.apache.directory.server.core.api.partition.Partition;
import org.apache.directory.server.core.api.schema.SchemaPartition;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmPartition;
import org.apache.directory.server.core.partition.ldif.LdifPartition;
import org.apache.directory.server.core.partition.ldif.SingleFileLdifPartition;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * User: Denis Mikhalkin
 * Date: 10/03/14
 * Time: 6:02 PM
 */
public class Runner {

    private static final Logger IAM_LOG = LoggerFactory.getLogger(Runner.class);
    private static int serverPort = 10389;
    private ApacheDSUtils utils;

    /**
     * The directory service
     */
    private DirectoryService service;
//    private ApacheDsService service;
    /**
     * The LDAP server
     */
    private LdapServer server;


    public Runner(DirectoryService service) {
        this.service = service;
        utils = new ApacheDSUtils(service);
    }

    public Runner() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("default.controls", "org.apache.directory.api.ldap.codec.controls.cascade.CascadeFactory,org.apache.directory.api.ldap.codec.controls.manageDsaIT.ManageDsaITFactory,org.apache.directory.api.ldap.codec.controls.search.entryChange.EntryChangeFactory,org.apache.directory.api.ldap.codec.controls.search.pagedSearch.PagedResultsFactory,org.apache.directory.api.ldap.codec.controls.search.persistentSearch.PersistentSearchFactory,org.apache.directory.api.ldap.codec.controls.search.subentries.SubentriesFactory");
        System.setProperty("extra.controls", "org.apache.directory.api.ldap.extras.controls.ppolicy_impl.PasswordPolicyFactory,org.apache.directory.api.ldap.extras.controls.syncrepl_impl.SyncDoneValueFactory,org.apache.directory.api.ldap.extras.controls.syncrepl_impl.SyncInfoValueFactory,org.apache.directory.api.ldap.extras.controls.syncrepl_impl.SyncRequestValueFactory,org.apache.directory.api.ldap.extras.controls.syncrepl_impl.SyncStateValueFactory");
        System.setProperty("default.extendedOperation.requests", "org.apache.directory.api.ldap.extras.extended.ads_impl.cancel.CancelFactory,org.apache.directory.api.ldap.extras.extended.ads_impl.certGeneration.CertGenerationFactory,org.apache.directory.api.ldap.extras.extended.ads_impl.gracefulShutdown.GracefulShutdownFactory,org.apache.directory.api.ldap.extras.extended.ads_impl.storedProcedure.StoredProcedureFactory");
        System.setProperty("default.extendedOperation.responses", "org.apache.directory.api.ldap.extras.extended.ads_impl.gracefulDisconnect.GracefulDisconnectFactory");

        Runner runner = new Runner();
        runner.initDirectoryService(getDirectoryPath(args));
        runner.startServer();
        System.out.println("Server started on " + serverPort);
    }

    private static File getDirectoryPath(String[] args) {
        if (args.length > 0) return new File(args[0]);
        if (!new File("/var").exists()) {
            return new File(System.getProperty("java.io.tmpdir"), "iam_ldap");
        } else {
            return new File("/var", "iam_ldap");
        }
    }

    /**
     * initialize the schema manager and add the schema partition to directory service
     *
     * @throws Exception if the schema LDIF files are not found on the classpath
     */
    private void initSchemaPartition() throws Exception {
        InstanceLayout instanceLayout = service.getInstanceLayout();

        File schemaPartitionDirectory = new File(instanceLayout.getPartitionsDirectory(), "schema");

        // Extract the schema on disk (a brand new one) and load the registries
        if (schemaPartitionDirectory.exists()) {
            System.out.println("schema partition already exists, skipping schema extraction");
        } else {
            SchemaLdifExtractor extractor = new DefaultSchemaLdifExtractor(instanceLayout.getPartitionsDirectory());
            extractor.extractOrCopy();
        }

        SchemaLoader loader = new LdifSchemaLoader(schemaPartitionDirectory);
        SchemaManager schemaManager = new DefaultSchemaManager(loader);

        // We have to load the schema now, otherwise we won't be able
        // to initialize the Partitions, as we won't be able to parse
        // and normalize their suffix Dn
        schemaManager.loadAllEnabled();

        List<Throwable> errors = schemaManager.getErrors();

        if (errors.size() != 0) {
            throw new Exception(I18n.err(I18n.ERR_317, Exceptions.printErrors(errors)));
        }

        service.setSchemaManager(schemaManager);

        // Init the LdifPartition with schema
        LdifPartition schemaLdifPartition = new LdifPartition(schemaManager, service.getDnFactory());
        schemaLdifPartition.setPartitionPath(schemaPartitionDirectory.toURI());

        // The schema partition
        SchemaPartition schemaPartition = new SchemaPartition(schemaManager);
        schemaPartition.setWrappedPartition(schemaLdifPartition);
        service.setSchemaPartition(schemaPartition);
    }

    /**
     * Initialize the server. It creates the partition, adds the index, and
     * injects the context entries for the created partitions.
     *
     * @param workDir the directory to be used for storing the data
     * @throws Exception if there were some problems while initializing the system
     */
    private void initDirectoryService(File workDir) throws Exception {
        // Initialize the LDAP service
        service = new DefaultDirectoryService();
        utils = new ApacheDSUtils(service);
//        service = new ApacheDsService();
//        service.start(new InstanceLayout( workDir ));
        service.setInstanceLayout(new InstanceLayout(workDir));

        CacheService cacheService = new CacheService();
        cacheService.initialize(service.getInstanceLayout());

        service.setCacheService(cacheService);

        // first load the schema
        initSchemaPartition();

        // then the system partition
        // this is a MANDATORY partition
        // DO NOT add this via addPartition() method, trunk code complains about duplicate partition
        // while initializing
        JdbmPartition systemPartition = new JdbmPartition(service.getSchemaManager(), service.getDnFactory());
        systemPartition.setId("system");
        systemPartition.setPartitionPath(new File(service.getInstanceLayout().getPartitionsDirectory(), systemPartition.getId()).toURI());
        systemPartition.setSuffixDn(new Dn(ServerDNConstants.SYSTEM_DN));
        systemPartition.setSchemaManager(service.getSchemaManager());

        // mandatory to call this method to set the system partition
        // Note: this system partition might be removed from trunk
        service.setSystemPartition(systemPartition);

        service.getChangeLog().setEnabled(false);
        service.setDenormalizeOpAttrsEnabled(true);

        SingleFileLdifPartition configPartition = new SingleFileLdifPartition(service.getSchemaManager(), service.getDnFactory());
        configPartition.setId("config");
        configPartition.setPartitionPath(new File(service.getInstanceLayout().getConfDirectory(), "config.ldif").toURI());
        configPartition.setSuffixDn(new Dn(service.getSchemaManager(), "ou=config"));
        configPartition.setSchemaManager(service.getSchemaManager());
        configPartition.setCacheService(cacheService);

        configPartition.initialize();
        service.addPartition(configPartition);

        readIAMProperties();

        String rootDN = AWSIAMAuthenticator.getConfig().rootDN;
        Partition iamPartition = utils.addPartition("iam", rootDN, service.getDnFactory());

        // Index some attributes on the apache partition
        utils.addIndex(iamPartition, "objectClass", "ou", "uid", "gidNumber", "uidNumber", "cn");

        // And start the service
        service.startup();

        utils.loadLdif("iam.ldif");
        utils.loadLdif("enable_nis.ldif");
        utils.loadLdif("auth.ldif");
        if (!utils.exists("cn=config,ads-authenticatorid=awsiamauthenticator,ou=authenticators,ads-interceptorId=authenticationInterceptor,ou=interceptors,ads-directoryServiceId=default,ou=config")) {
            Entry entryIAM = service.newEntry(service.getDnFactory().create("cn=config,ads-authenticatorid=awsiamauthenticator,ou=authenticators,ads-interceptorId=authenticationInterceptor,ou=interceptors,ads-directoryServiceId=default,ou=config"));
            entryIAM.put("objectClass", "iamauthenticatorconfig", "top");
            entryIAM.put(SchemaConstants.ENTRY_CSN_AT, service.getCSN().toString());
            entryIAM.put(SchemaConstants.ENTRY_UUID_AT, UUID.randomUUID().toString());
            entryIAM.put("cn", "config");
            entryIAM.put("idGenerator", "1000");
            service.getAdminSession().add(entryIAM);
        }
        Dn dnIAM = service.getDnFactory().create(rootDN);
        if (!service.getAdminSession().exists(dnIAM)) {
            Entry entryIAM = new DefaultEntry(service.getSchemaManager(), dnIAM, "objectClass: top", "objectClass: domain", "dc: iam",
                    "entryCsn: " + service.getCSN(), SchemaConstants.ENTRY_UUID_AT + ": " + UUID.randomUUID().toString());
            iamPartition.add(new AddOperationContext(null, entryIAM));
        }
    }

    public void createStructure() throws Exception {
        String rootDN = AWSIAMAuthenticator.getConfig().rootDN;
        Dn dnIAM = service.getDnFactory().create(rootDN);
        if (!utils.exists(dnIAM)) {
            IAM_LOG.info("Creating partition " + rootDN);
            Partition iamPartition = utils.addPartition("iam", rootDN, service.getDnFactory());

            // Index some attributes on the apache partition
            utils.addIndex(iamPartition, "objectClass", "ou", "uid", "gidNumber", "uidNumber", "cn");
            service.sync();

            if (!utils.exists(dnIAM)) {
                IAM_LOG.info("Creating root node " + rootDN);
                Rdn rdn = dnIAM.getRdn(0);
                String _type = rdn.getType();
                String _value = rdn.getValue();
                Entry entryIAM = new DefaultEntry(service.getSchemaManager());
                entryIAM.setDn(dnIAM);
                entryIAM.add("objectClass", "top", "domain");
                entryIAM.add("entryCsn", service.getCSN().toString());
                entryIAM.add(SchemaConstants.ENTRY_UUID_AT, UUID.randomUUID().toString());
                entryIAM.add(_type, _value);
                assert entryIAM.isSchemaAware();


                CoreSession session = service.getAdminSession();
                session.add(entryIAM, null);
                this.checkErrors();
            }
        }
        service.sync();
    }

    public void checkErrors() {
        if (!service.getSchemaManager().getErrors().isEmpty()) {
            throw new RuntimeException("Errors: " + service.getSchemaManager().getErrors());
        }
    }

    private void readIAMProperties() {
        String propsPath = System.getProperty("iamLdapPropertiesPath", "/etc/iam_ldap.conf");
        File propsFile = new File(propsPath);
        // Read the config file if exists
        if (propsFile.exists()) {
            try {
                Properties props = new Properties();
                props.load(new FileInputStream(propsFile));
                AWSIAMAuthenticator.Config config = new AWSIAMAuthenticator.Config();
                if (props.containsKey("pollPeriod"))
                    config.pollPeriod = Integer.parseInt(props.getProperty("pollPeriod"));
                if (props.containsKey("rootDN")) config.rootDN = props.getProperty("rootDN");
                AWSIAMAuthenticator.setConfig(config);
            } catch (IOException e) {
                IAM_LOG.error("Unable to read IAM LDAP config file");
                AWSIAMAuthenticator.setConfig(new AWSIAMAuthenticator.Config());
            }
        } else {
            // Populate from defaults
            AWSIAMAuthenticator.setConfig(new AWSIAMAuthenticator.Config());
        }
    }

    /**
     * starts the LdapServer
     *
     * @throws Exception
     */
    public void startServer() throws Exception {
        server = new LdapServer();

        server.setTransports(new TcpTransport(serverPort));
        server.setDirectoryService(service);

        server.start();
    }

    public Partition getPartition(DirectoryService directory, String id) throws LdapException {
        Set<? extends Partition> partitions = directory.getPartitions();
        for (Partition part : partitions) {
            if (part.getId().equalsIgnoreCase(id)) return part;
        }
        throw new LdapException("No partition with the ID " + id);
    }
}
