package co.omnistream.domain;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import co.omnistream.domain.exception.DomainException;
import co.omnistream.domain.model.access.User;
import co.omnistream.domain.model.catalog.CatalogIndex;
import co.omnistream.domain.model.catalog.CatalogItem;
import co.omnistream.domain.model.catalog.Collection;
import co.omnistream.domain.model.catalog.MediaFile;
import co.omnistream.domain.model.playback.PlaybackSession;
import co.omnistream.domain.model.sharing.SharedLink;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules of the domain, checked on every build (ADR 0020). They turn ADR 0011, ADR 0019
 * and the coding conventions into tests: if one fails, the code broke a design decision.
 */
class ArchitectureTest {

  private static final JavaClasses DOMAIN =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("co.omnistream.domain");

  // ---------------------------------------------------------------- layers inside the domain

  @Test
  void theModelDoesNotKnowServicesOrPorts() {
    noClasses()
        .that()
        .resideInAnyPackage(
            "..domain.model..",
            "..domain.event..",
            "..domain.snapshot..",
            "..domain.exception..",
            "..domain.policy..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..domain.service..", "..domain.port..")
        .because("the model is the core of the hexagon (ADR 0019)")
        .check(DOMAIN);
  }

  @Test
  void portsDoNotKnowServices() {
    noClasses()
        .that()
        .resideInAPackage("..domain.port..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..domain.service..")
        .because("services implement the ports, not the other way round (ADR 0019)")
        .check(DOMAIN);
  }

  @Test
  void portsAreInterfaces() {
    classes()
        .that()
        .resideInAnyPackage("..domain.port.in..", "..domain.port.out..")
        .should()
        .beInterfaces()
        .because("ports are contracts; their implementations live elsewhere (ADR 0018)")
        .check(DOMAIN);
  }

  @Test
  void everyDomainServiceImplementsADrivingPort() {
    classes()
        .that()
        .resideInAPackage("co.omnistream.domain.service")
        .and()
        .haveSimpleNameEndingWith("Service")
        .and()
        .doNotHaveModifier(JavaModifier.ABSTRACT)
        .should()
        .implement(resideInAPackage("..domain.port.in.."))
        .because("primary adapters reach the services only through driving ports")
        .check(DOMAIN);
  }

  @Test
  void theDomainDoesNotUseJdbc() {
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("java.sql..", "javax.sql..")
        .because("persistence belongs to the infrastructure (ADR 0015)")
        .check(DOMAIN);
  }

  // ---------------------------------------------------------------- aggregates (ADR 0011)

  @Test
  void otherAggregatesReferToTheCatalogOnlyByIdentifier() {
    noClasses()
        .that()
        .resideInAnyPackage("..model.access..", "..model.sharing..", "..model.playback..")
        .should()
        .dependOnClassesThat()
        .belongToAnyOf(CatalogIndex.class, CatalogItem.class, MediaFile.class, Collection.class)
        .because("aggregates refer to each other by identifier")
        .check(DOMAIN);
  }

  @Test
  void otherAggregatesReferToUsersOnlyByIdentifier() {
    noClasses()
        .that()
        .resideInAnyPackage("..model.catalog..", "..model.sharing..", "..model.playback..")
        .should()
        .dependOnClassesThat()
        .belongToAnyOf(User.class)
        .because("aggregates refer to each other by identifier")
        .check(DOMAIN);
  }

  @Test
  void otherAggregatesReferToLinksAndSessionsOnlyByIdentifier() {
    noClasses()
        .that()
        .resideInAnyPackage("..model.catalog..", "..model.access..")
        .should()
        .dependOnClassesThat()
        .belongToAnyOf(SharedLink.class, PlaybackSession.class)
        .because("aggregates refer to each other by identifier")
        .check(DOMAIN);
  }

  @Test
  void snapshotsAreRecords() {
    classes()
        .that()
        .resideInAPackage("..domain.snapshot..")
        .and()
        .areNotInterfaces()
        .should()
        .beAssignableTo(Record.class)
        .because("snapshots are immutable (ADR 0011)")
        .check(DOMAIN);
  }

  // ---------------------------------------------------------------- coding conventions

  @Test
  void entitiesHaveNoSetters() {
    methods()
        .that()
        .arePublic()
        .and()
        .areDeclaredInClassesThat()
        .resideInAPackage("..domain.model..")
        .should()
        .haveNameNotMatching("set[A-Z].*")
        .because("entities change only through methods that express a rule (conventions, 3)")
        .check(DOMAIN);
  }

  @Test
  void theDomainNeverReadsTheClock() {
    noClasses()
        .should()
        .callMethod(Instant.class, "now")
        .orShould()
        .callMethod(LocalDateTime.class, "now")
        .orShould()
        .callMethod(LocalDate.class, "now")
        .orShould()
        .callMethod(ZonedDateTime.class, "now")
        .orShould()
        .callMethod(System.class, "currentTimeMillis")
        .orShould()
        .callMethod(Clock.class, "systemUTC")
        .orShould()
        .callMethod(Clock.class, "systemDefaultZone")
        .because("services inject a Clock and pass the instant (conventions, 6)")
        .check(DOMAIN);
  }

  @Test
  void domainExceptionsExtendDomainException() {
    classes()
        .that()
        .resideInAPackage("..domain.exception..")
        .and()
        .doNotHaveSimpleName("package-info")
        .should()
        .beAssignableTo(DomainException.class)
        .because("the REST API maps every DomainException to an HTTP status")
        .check(DOMAIN);
  }
}
