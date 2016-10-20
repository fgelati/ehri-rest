package eu.ehri.project.exporters.ead;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import eu.ehri.project.api.Api;
import eu.ehri.project.api.QueryApi;
import eu.ehri.project.definitions.ContactInfo;
import eu.ehri.project.definitions.IsadG;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.exporters.xml.AbstractStreamingXmlExporter;
import eu.ehri.project.models.AccessPoint;
import eu.ehri.project.models.AccessPointType;
import eu.ehri.project.models.Address;
import eu.ehri.project.models.DatePeriod;
import eu.ehri.project.models.DocumentaryUnit;
import eu.ehri.project.models.DocumentaryUnitDescription;
import eu.ehri.project.models.Repository;
import eu.ehri.project.models.RepositoryDescription;
import eu.ehri.project.models.base.Description;
import eu.ehri.project.models.base.Entity;
import eu.ehri.project.models.events.SystemEvent;
import eu.ehri.project.utils.LanguageHelpers;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamWriter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;


public class Ead2002Exporter extends AbstractStreamingXmlExporter<DocumentaryUnit> implements EadExporter {

    private static final Logger logger = LoggerFactory.getLogger(Ead2002Exporter.class);
    private static final DateTimeFormatter unitDateNormalFormat = DateTimeFormat.forPattern("YYYYMMdd");

    private static final String DEFAULT_NAMESPACE = "urn:isbn:1-931666-22-9";
    private static final Map<String, String> NAMESPACES = namespaces(
            "xlink", "http://www.w3.org/1999/xlink",
            "xsi", "http://www.w3.org/2001/XMLSchema-instance"
    );

    private static final Map<IsadG, String> multiValueTextMappings = ImmutableMap.<IsadG, String>builder()
            .put(IsadG.scopeAndContent, "scopecontent")
            .put(IsadG.datesOfDescriptions, "processinfo")
            .put(IsadG.systemOfArrangement, "arrangement")
            .put(IsadG.publicationNote, "bibliography")
            .put(IsadG.locationOfCopies, "altformavail")
            .put(IsadG.locationOfOriginals, "originalsloc")
            .put(IsadG.biographicalHistory, "bioghist")
            .put(IsadG.conditionsOfAccess, "accessrestrict")
            .put(IsadG.conditionsOfReproduction, "userestrict")
            .put(IsadG.findingAids, "otherfindaid")
            .put(IsadG.accruals, "accruals")
            .put(IsadG.acquisition, "acqinfo")
            .put(IsadG.appraisal, "appraisal")
            .put(IsadG.archivalHistory, "custodhist")
            .put(IsadG.physicalCharacteristics, "phystech")
            .put(IsadG.relatedUnitsOfDescription, "relatedmaterial")
            .put(IsadG.separatedUnitsOfDescription, "separatedmaterial")
            .put(IsadG.notes, "odd") // controversial!
            .build();

    private static final Map<IsadG, String> textDidMappings = ImmutableMap.<IsadG, String>builder()
            .put(IsadG.extentAndMedium, "physdesc")
            .put(IsadG.unitDates, "unitdate")
            .build();

    private static final Map<AccessPointType, String> controlAccessMappings = ImmutableMap.<AccessPointType, String>builder()
            .put(AccessPointType.subject, "subject")
            .put(AccessPointType.person, "persname")
            .put(AccessPointType.family, "famname")
            .put(AccessPointType.corporateBody, "corpname")
            .put(AccessPointType.place, "geogname")
            .put(AccessPointType.genre, "genreform")
            .build();

    private static final List<ContactInfo> addressKeys = ImmutableList
            .of(ContactInfo.street,
                    ContactInfo.postalCode,
                    ContactInfo.municipality,
                    ContactInfo.firstdem,
                    ContactInfo.countryCode,
                    ContactInfo.telephone,
                    ContactInfo.fax,
                    ContactInfo.webpage,
                    ContactInfo.email);

    private final Api api;

    public Ead2002Exporter(Api api) {
        this.api = api;
    }

    @Override
    public void export(XMLStreamWriter sw, DocumentaryUnit unit, String langCode) {

        root(sw, "ead", DEFAULT_NAMESPACE, attrs(), NAMESPACES, () -> {
            attribute(sw, "http://www.w3.org/2001/XMLSchema-instance",
                    "schemaLocation", DEFAULT_NAMESPACE + " http://www.loc.gov/ead/ead.xsd");

            Repository repository = unit.getRepository();
            Optional<Description> descOpt = LanguageHelpers.getBestDescription(
                    unit, Optional.empty(), langCode);

            tag(sw, "eadheader", attrs("relatedencoding", "DC",
                    "scriptencoding", "iso15924",
                    "repositoryencoding", "iso15511",
                    "dateencoding", "iso8601",
                    "countryencoding", "iso3166-1"), () -> {

                tag(sw, "eadid", unit.getId());
                descOpt.ifPresent(desc -> {
                    addFileDesc(sw, langCode, repository, desc);
                    addProfileDesc(sw, desc);
                });
                addRevisionDesc(sw, unit);
            });

            descOpt.ifPresent(desc -> {
                tag(sw, "archdesc", getLevelAttrs(descOpt, "collection"), () -> {
                    addDataSection(sw, repository, unit, desc, langCode);
                    addPropertyValues(sw, desc);
                    Iterable<DocumentaryUnit> orderedChildren = getOrderedChildren(unit);
                    if (orderedChildren.iterator().hasNext()) {
                        tag(sw, "dsc", () -> {
                            for (DocumentaryUnit child : orderedChildren) {
                                addEadLevel(sw, 1, child, descOpt, langCode);
                            }
                        });
                    }
                    addControlAccess(sw, desc);
                });
            });
        });
    }

    private void addProfileDesc(XMLStreamWriter sw, Description desc) {
        tag(sw, "profiledesc", () -> {
            tag(sw, "creation", () -> {
                characters(sw, resourceAsString("export-boilerplate.txt"));
                DateTime now = DateTime.now();
                tag(sw, "date", now.toString(), attrs("normal", unitDateNormalFormat.print(now)
                ));
            });
            tag(sw, "langusage", () -> tag(sw, "language",
                    LanguageHelpers.codeToName(desc.getLanguageOfDescription()),
                    attrs("langcode", desc.getLanguageOfDescription())
            ));
            Optional.ofNullable(desc.<String>getProperty(IsadG.rulesAndConventions)).ifPresent(value ->
                    tag(sw, "descrules", value, attrs("encodinganalog", "3.7.2"))
            );
        });
    }

    private void addFileDesc(XMLStreamWriter sw, String langCode, Repository repository, Description desc) {
        tag(sw, "filedesc", () -> {
            tag(sw, "titlestmt", () -> tag(sw, "titleproper", desc.getName()));
            tag(sw, "publicationstmt", () -> {
                LanguageHelpers.getBestDescription(
                        repository, Optional.empty(), langCode).ifPresent(repoDesc -> {
                    tag(sw, "publisher", repoDesc.getName());
                    for (Address address : repoDesc.as(RepositoryDescription.class).getAddresses()) {
                        tag(sw, "address", () -> {
                            for (ContactInfo key : addressKeys) {
                                for (Object v : coerceList(address.getProperty(key))) {
                                    tag(sw, "addressline", v.toString());
                                }
                            }
                            tag(sw, "addressline",
                                    LanguageHelpers.countryCodeToName(
                                            repository.getCountry().getId()));
                        });
                    }
                });
            });
            if (Description.CreationProcess.IMPORT.equals(desc.getCreationProcess())) {
                tag(sw, ImmutableList.of("notestmt", "note", "p"), resourceAsString("creationprocess-boilerplate.txt"));
            }
        });
    }

    private void addRevisionDesc(XMLStreamWriter sw, DocumentaryUnit unit) {
        List<List<SystemEvent>> eventList = Lists.newArrayList(api.events().aggregateForItem(unit));
        if (!eventList.isEmpty()) {
            tag(sw, "revisiondesc", () -> {
                for (int i = eventList.size() - 1; i >= 0; i--) {
                    List<SystemEvent> agg = eventList.get(i);
                    SystemEvent event = agg.get(0);
                    tag(sw, "change", () -> {
                        tag(sw, "date", new DateTime(event.getTimestamp()).toString());
                        if (event.getLogMessage() == null || event.getLogMessage().isEmpty()) {
                            tag(sw, "item", event.getEventType().name());
                        } else {
                            tag(sw, "item", String.format("%s [%s]",
                                    event.getLogMessage(), event.getEventType()));
                        }
                    });
                }
            });
        }
    }

    private void addDataSection(XMLStreamWriter sw, Repository repository, DocumentaryUnit subUnit,
            Description desc, String langCode) {
        tag(sw, "did", () -> {
            tag(sw, "unitid", subUnit.getIdentifier());
            tag(sw, "unittitle", desc.getName(), attrs("encodinganalog", "3.1.2"));

            for (DatePeriod datePeriod : desc.as(DocumentaryUnitDescription.class).getDatePeriods()) {
                if (DatePeriod.DatePeriodType.creation.equals(datePeriod.getDateType())) {
                    String start = datePeriod.getStartDate();
                    String end = datePeriod.getEndDate();
                    if (start != null && end != null) {
                        DateTime startDateTime = new DateTime(start);
                        DateTime endDateTime = new DateTime(end);
                        String normal = String.format("%s/%s",
                                unitDateNormalFormat.print(startDateTime),
                                unitDateNormalFormat.print(endDateTime));
                        String text = String.format("%s/%s",
                                startDateTime.year().get(), endDateTime.year().get());
                        tag(sw, "unitdate", text, attrs("normal", normal, "encodinganalog", "3.1.3"));
                    } else if (start != null) {
                        DateTime startDateTime = new DateTime(start);
                        String normal = String.format("%s",
                                unitDateNormalFormat.print(startDateTime));
                        String text = String.format("%s", startDateTime.year().get());
                        tag(sw, "unitdate", text, attrs("normal", normal, "encodinganalog", "3.1.3"));
                    }
                }
            }

            Set<String> propertyKeys = desc.getPropertyKeys();
            for (Map.Entry<IsadG, String> pair : textDidMappings.entrySet()) {
                if (propertyKeys.contains(pair.getKey().name())) {
                    for (Object v : coerceList(desc.getProperty(pair.getKey()))) {
                        tag(sw, pair.getValue(), v.toString(), textFieldAttrs(pair.getKey()));
                    }
                }
            }

            if (propertyKeys.contains(IsadG.languageOfMaterial.name())) {
                tag(sw, "langmaterial", () -> {
                    for (Object v : coerceList(desc.getProperty(IsadG.languageOfMaterial))) {
                        String langName = LanguageHelpers.codeToName(v.toString());
                        if (v.toString().length() != 3) {
                            tag(sw, "language", langName, textFieldAttrs(IsadG.languageOfMaterial));
                        } else {
                            tag(sw, "language", langName, textFieldAttrs(IsadG.languageOfMaterial, "langcode", v
                                    .toString()));
                        }
                    }
                });
            }

            Optional.ofNullable(repository).ifPresent(repo -> {
                LanguageHelpers.getBestDescription(repo, Optional.empty(), langCode).ifPresent(repoDesc ->
                        tag(sw, "repository", () ->
                                tag(sw, "corpname", repoDesc.getName()))
                );
            });
        });
    }

    private void addEadLevel(XMLStreamWriter sw, int num, DocumentaryUnit subUnit,
            Optional<Description> priorDescOpt, String langCode) {
        logger.trace("Adding EAD sublevel: c" + num);
        Optional<Description> descOpt = LanguageHelpers.getBestDescription(subUnit, priorDescOpt, langCode);
        String levelTag = String.format("c%02d", num);
        tag(sw, levelTag, getLevelAttrs(descOpt, null), () -> {
            descOpt.ifPresent(desc -> {
                addDataSection(sw, null, subUnit, desc, langCode);
                addPropertyValues(sw, desc);
                addControlAccess(sw, desc);
            });

            for (DocumentaryUnit child : getOrderedChildren(subUnit)) {
                addEadLevel(sw, num + 1, child, descOpt, langCode);
            }
        });
    }

    private void addControlAccess(XMLStreamWriter sw, Description desc) {
        Map<AccessPointType, List<AccessPoint>> byType = Maps.newHashMap();
        for (AccessPoint accessPoint : desc.getAccessPoints()) {
            AccessPointType type = accessPoint.getRelationshipType();
            if (controlAccessMappings.containsKey(type)) {
                if (byType.containsKey(type)) {
                    byType.get(type).add(accessPoint);
                } else {
                    byType.put(type, Lists.newArrayList(accessPoint));
                }
            }
        }

        for (Map.Entry<AccessPointType, List<AccessPoint>> entry : byType.entrySet()) {
            tag(sw, "controlaccess", () -> {
                AccessPointType type = entry.getKey();
                for (AccessPoint accessPoint : entry.getValue()) {
                    tag(sw, controlAccessMappings.get(type), accessPoint.getName());
                }
            });
        }
    }

    private void addPropertyValues(XMLStreamWriter sw, Entity item) {
        Set<String> available = item.getPropertyKeys();
        for (Map.Entry<IsadG, String> pair : multiValueTextMappings.entrySet()) {
            if (available.contains(pair.getKey().name())) {
                for (Object v : coerceList(item.getProperty(pair.getKey()))) {
                    tag(sw, pair.getValue(), textFieldAttrs(pair.getKey()),
                            () -> tag(sw, "p", () -> cData(sw, v.toString()))
                    );
                }
            }
        }
    }

    private Map<String, String> textFieldAttrs(IsadG field, String... kvs) {
        Preconditions.checkArgument(kvs.length % 2 == 0);
        Map<String, String> attrs = field.getAnalogueEncoding()
                .map(Collections::singleton)
                .orElse(Collections.emptySet())
                .stream().collect(Collectors.toMap(e -> "encodinganalog", e -> e));
        for (int i = 0; i < kvs.length; i += 2) {
            attrs.put(kvs[0], kvs[i + 1]);
        }
        return attrs;
    }

    private Map<String, String> getLevelAttrs(Optional<Description> descOpt, String defaultLevel) {
        String level = descOpt
                .map(d -> d.<String>getProperty(IsadG.levelOfDescription))
                .orElse(defaultLevel);
        return level != null ? ImmutableMap.of("level", level) : Collections.emptyMap();
    }

    // Sort the children by identifier. FIXME: This might be a bad assumption!
    private Iterable<DocumentaryUnit> getOrderedChildren(DocumentaryUnit unit) {
        return api
                .query()
                .orderBy(Ontology.IDENTIFIER_KEY, QueryApi.Sort.ASC)
                .setLimit(-1)
                .setStream(true)
                .page(unit.getChildren(), DocumentaryUnit.class);
    }
}
