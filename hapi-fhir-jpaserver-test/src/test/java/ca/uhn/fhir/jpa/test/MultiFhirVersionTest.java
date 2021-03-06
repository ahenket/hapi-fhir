package ca.uhn.fhir.jpa.test;

import static org.junit.Assert.*;

import java.util.List;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.provider.JpaSystemProvider;
import ca.uhn.fhir.jpa.testutil.RandomServerPortProvider;
import ca.uhn.fhir.model.api.Bundle;
import ca.uhn.fhir.model.dev.resource.SupportingDocumentation;
import ca.uhn.fhir.model.dev.valueset.AdministrativeGenderEnum;
import ca.uhn.fhir.model.dstu.resource.Patient;
import ca.uhn.fhir.model.dstu.valueset.AdministrativeGenderCodesEnum;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.model.primitive.StringDt;
import ca.uhn.fhir.rest.client.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.RestfulServer;

public class MultiFhirVersionTest {

	private static ClassPathXmlApplicationContext ourAppCtx;
	private static IGenericClient ourClientDev;
	private static Server ourServer;
	private static IGenericClient ourClientDstu1;

	@AfterClass
	public static void afterClass() throws Exception {
		ourServer.stop();
		ourAppCtx.stop();
	}

	@Test
	public void testSubmitPatient() {
		Patient p = new Patient();
		p.addIdentifier("urn:MultiFhirVersionTest", "testSubmitPatient01");
		p.addUndeclaredExtension(false, "http://foo#ext1", new StringDt("The value"));
		p.getGender().setValueAsEnum(AdministrativeGenderCodesEnum.M);
		IdDt id = ourClientDstu1.create().resource(p).execute().getId();

		// Read back as DSTU1
		Patient patDstu1 = ourClientDstu1.read(Patient.class, id);
		assertEquals("testSubmitPatient01", p.getIdentifierFirstRep().getValue().getValue());
		assertEquals(1, patDstu1.getUndeclaredExtensionsByUrl("http://foo#ext1").size());
		assertEquals("M", patDstu1.getGender().getCodingFirstRep().getCode().getValue());

		// Read back as DEV
		ca.uhn.fhir.model.dev.resource.Patient patDev;
		patDev = ourClientDev.read(ca.uhn.fhir.model.dev.resource.Patient.class, id);
		assertEquals("testSubmitPatient01", p.getIdentifierFirstRep().getValue().getValue());
		assertEquals(1, patDev.getUndeclaredExtensionsByUrl("http://foo#ext1").size());
		assertEquals(null, patDev.getGender());

		// Search using new bundle format
		Bundle bundle = ourClientDev.search().forResource(ca.uhn.fhir.model.dev.resource.Patient.class).where(Patient.IDENTIFIER.exactly().systemAndCode("urn:MultiFhirVersionTest", "testSubmitPatient01")).encodedJson().execute();
		patDev = (ca.uhn.fhir.model.dev.resource.Patient) bundle.getEntries().get(0).getResource();
		assertEquals("testSubmitPatient01", p.getIdentifierFirstRep().getValue().getValue());
		assertEquals(1, patDev.getUndeclaredExtensionsByUrl("http://foo#ext1").size());
		assertEquals(null, patDev.getGender());

	}

	@Test
	public void testSubmitPatientDev() {
		ca.uhn.fhir.model.dev.resource.Patient p = new ca.uhn.fhir.model.dev.resource.Patient();
		p.addIdentifier().setSystem("urn:MultiFhirVersionTest").setValue("testSubmitPatientDev01");
		p.addUndeclaredExtension(false, "http://foo#ext1", new StringDt("The value"));
		p.setGender(AdministrativeGenderEnum.MALE);
		IdDt id = ourClientDev.create().resource(p).execute().getId();

		// Read back as DSTU1
		Patient patDstu1 = ourClientDstu1.read(Patient.class, id);
		assertEquals("testSubmitPatientDev01", p.getIdentifierFirstRep().getValue());
		assertEquals(1, patDstu1.getUndeclaredExtensionsByUrl("http://foo#ext1").size());
		assertEquals(null, patDstu1.getGender().getCodingFirstRep().getCode().getValue());

		// Read back as DEV
		ca.uhn.fhir.model.dev.resource.Patient patDev;
		patDev = ourClientDev.read(ca.uhn.fhir.model.dev.resource.Patient.class, id);
		assertEquals("testSubmitPatientDev01", p.getIdentifierFirstRep().getValue());
		assertEquals(1, patDev.getUndeclaredExtensionsByUrl("http://foo#ext1").size());
		assertEquals("male", patDev.getGender());

		// Search using new bundle format
		Bundle bundle = ourClientDev.search().forResource(ca.uhn.fhir.model.dev.resource.Patient.class).where(Patient.IDENTIFIER.exactly().systemAndCode("urn:MultiFhirVersionTest", "testSubmitPatientDev01")).encodedJson().execute();
		patDev = (ca.uhn.fhir.model.dev.resource.Patient) bundle.getEntries().get(0).getResource();
		assertEquals("testSubmitPatientDev01", p.getIdentifierFirstRep().getValue());
		assertEquals(1, patDev.getUndeclaredExtensionsByUrl("http://foo#ext1").size());
		assertEquals("male", patDev.getGender());

	}

	@Test
	public void testUnknownResourceType() {
		ca.uhn.fhir.model.dev.resource.Patient p = new ca.uhn.fhir.model.dev.resource.Patient();
		p.addIdentifier().setSystem("urn:MultiFhirVersionTest").setValue("testUnknownResourceType01");
		IdDt id = ourClientDev.create().resource(p).execute().getId();

		SupportingDocumentation s = new SupportingDocumentation();
		s.addIdentifier().setSystem("urn:MultiFhirVersionTest").setValue("testUnknownResourceType02");
		id = ourClientDev.create().resource(s).execute().getId();

		Bundle history = ourClientDev.history(null, id, null, null);
		assertEquals(SupportingDocumentation.class, history.getEntries().get(0).getResource().getClass());
		assertEquals(ca.uhn.fhir.model.dev.resource.Patient.class, history.getEntries().get(1).getResource().getClass());

		history = ourClientDstu1.history(null, id, null, null);
		assertEquals(ca.uhn.fhir.model.dstu.resource.Patient.class, history.getEntries().get(0).getResource().getClass());
	}

	
	@SuppressWarnings("unchecked")
	@BeforeClass
	public static void beforeClass() throws Exception {
		ourAppCtx = new ClassPathXmlApplicationContext("hapi-fhir-server-resourceproviders-dev.xml", "hapi-fhir-server-resourceproviders-dstu1.xml", "fhir-spring-test-config.xml");

		int port = RandomServerPortProvider.findFreePort();
		ServletContextHandler proxyHandler = new ServletContextHandler();
		proxyHandler.setContextPath("/");

		ourServer = new Server(port);

		/*
		 * DEV resources
		 */

		RestfulServer restServerDev = new RestfulServer();
		restServerDev.setFhirContext(ourAppCtx.getBean("myFhirContextDev", FhirContext.class));
		List<IResourceProvider> rpsDev = (List<IResourceProvider>) ourAppCtx.getBean("myResourceProvidersDev", List.class);
		restServerDev.setResourceProviders(rpsDev);

		JpaSystemProvider systemProvDev = (JpaSystemProvider) ourAppCtx.getBean("mySystemProviderDev", JpaSystemProvider.class);
		restServerDev.setPlainProviders(systemProvDev);

		ServletHolder servletHolder = new ServletHolder();
		servletHolder.setServlet(restServerDev);
		proxyHandler.addServlet(servletHolder, "/fhir/contextDev/*");

		/*
		 * DSTU resources
		 */

		RestfulServer restServerDstu1 = new RestfulServer();
		restServerDstu1.setFhirContext(ourAppCtx.getBean("myFhirContextDstu1", FhirContext.class));
		List<IResourceProvider> rpsDstu1 = (List<IResourceProvider>) ourAppCtx.getBean("myResourceProvidersDstu1", List.class);
		restServerDstu1.setResourceProviders(rpsDstu1);

		JpaSystemProvider systemProvDstu1 = (JpaSystemProvider) ourAppCtx.getBean("mySystemProviderDstu1", JpaSystemProvider.class);
		restServerDstu1.setPlainProviders(systemProvDstu1);

		servletHolder = new ServletHolder();
		servletHolder.setServlet(restServerDstu1);
		proxyHandler.addServlet(servletHolder, "/fhir/contextDstu1/*");

		/*
		 * Start server
		 */
		ourServer.setHandler(proxyHandler);
		ourServer.start();

		/*
		 * DEV Client
		 */
		String serverBaseDev = "http://localhost:" + port + "/fhir/contextDev";
		FhirContext ctxDev = ourAppCtx.getBean("myFhirContextDev", FhirContext.class);
		ctxDev.getRestfulClientFactory().setSocketTimeout(600 * 1000);
		ourClientDev = ctxDev.newRestfulGenericClient(serverBaseDev);
		ourClientDev.registerInterceptor(new LoggingInterceptor(true));

		/*
		 * DSTU1 Client
		 */
		String serverBaseDstu1 = "http://localhost:" + port + "/fhir/contextDstu1";
		FhirContext ctxDstu1 = ourAppCtx.getBean("myFhirContextDstu1", FhirContext.class);
		ctxDstu1.getRestfulClientFactory().setSocketTimeout(600 * 1000);
		ourClientDstu1 = ctxDstu1.newRestfulGenericClient(serverBaseDstu1);
		ourClientDstu1.registerInterceptor(new LoggingInterceptor(true));
	}

}
