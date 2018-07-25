package fi.fmi.avi.converter.iwxxm;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;

import net.opengis.gml32.AbstractTimeObjectType;
import net.opengis.gml32.TimeInstantPropertyType;
import net.opengis.gml32.TimeInstantType;
import net.opengis.gml32.TimePeriodPropertyType;
import net.opengis.gml32.TimePeriodType;
import net.opengis.gml32.TimePositionType;
import net.opengis.om20.TimeObjectPropertyType;

import org.springframework.util.StringUtils;

import fi.fmi.avi.model.PartialOrCompleteTimeInstant;
import fi.fmi.avi.model.PartialOrCompleteTimePeriod;

/**
 * Helpers for creating and handling JAXB generated content classes.
 */
public abstract class IWXXMConverterBase {
    private static JAXBContext jaxbCtx = null;
    private final static Map<String, Object> classToObjectFactory = new HashMap<>();
    private final static Map<String, Object> objectFactoryMap = new HashMap<>();

    /**
     * Singleton for accessing the shared JAXBContext for IWXXM JAXB handling.
     *
     * NOTE: this can take several seconds when done for the first time after JVM start,
     * needs to scan all the jars in classpath.
     *
     * @return the context
     * @throws JAXBException if the context cannot be created
     */
    public static synchronized JAXBContext getJAXBContext() throws JAXBException {
        if (jaxbCtx == null) {
            jaxbCtx = JAXBContext.newInstance("icao.iwxxm21:aero.aixm511:net.opengis.gml32:org.iso19139.ogc2007.gmd:org.iso19139.ogc2007.gco:org"
                    + ".iso19139.ogc2007.gss:org.iso19139.ogc2007.gts:org.iso19139.ogc2007.gsr:net.opengis.om20:net.opengis.sampling:net.opengis.sampling"
                    + ".spatial:wmo.metce2013:wmo.opm2013:org.w3c.xlink11");
        }
        return jaxbCtx;
    }

    public static <T> T create(final Class<T> clz) throws IllegalArgumentException {
        return create(clz, null);
    }

    @SuppressWarnings("unchecked")
    public static <T> T create(final Class<T> clz, final Consumer<T> consumer) throws IllegalArgumentException {
        Object result = null;
        Object objectFactory = getObjectFactory(clz);
        if (objectFactory != null) {
            String methodName = null;
            if (clz.getEnclosingClass() != null) {
                Class<?> encClass = clz.getEnclosingClass();
                StringBuilder sb = new StringBuilder("create").append(encClass.getSimpleName().substring(0, 1).toUpperCase())
                        .append(encClass.getSimpleName().substring(1));
                while (encClass.getEnclosingClass() != null) {
                    sb.append(clz.getSimpleName());
                    encClass = encClass.getEnclosingClass();
                }
                methodName = sb.append(clz.getSimpleName()).toString();
            } else {
                methodName = new StringBuilder("create").append(clz.getSimpleName().substring(0, 1).toUpperCase())
                        .append(clz.getSimpleName().substring(1))
                        .toString();
            }
            try {
                Method toCall = objectFactory.getClass().getMethod(methodName);
                result = toCall.invoke(objectFactory);
            } catch (ClassCastException | NoSuchMethodException | IllegalAccessException | IllegalAccessError | InvocationTargetException | IllegalArgumentException e) {
                throw new IllegalArgumentException("Unable to create JAXB element object for type " + clz, e);
            }
            if (consumer != null) {
                consumer.accept((T) result);
            }
        } else {
            throw new IllegalArgumentException("Unable to find ObjectFactory for JAXB element type " + clz);
        }
        return (T) result;
    }

    public static <T> JAXBElement<T> createAndWrap(Class<T> clz) {
        return createAndWrap(clz, null);
    }

    public static <T> JAXBElement<T> createAndWrap(Class<T> clz, final Consumer<T> consumer) {
        T element = create(clz);
        return wrap(element, clz, consumer);
    }

    public static <T> JAXBElement<T> wrap(T element, Class<T> clz) {
        return wrap(element, clz, null);
    }

    @SuppressWarnings("unchecked")
    public static <T> JAXBElement<T> wrap(T element, Class<T> clz, final Consumer<T> consumer) {
        Object result = null;
        Object objectFactory = getObjectFactory(clz);
        if (objectFactory != null) {
            String methodName = new StringBuilder("create").append(clz.getSimpleName().substring(0, 1).toUpperCase())
                    .append(clz.getSimpleName().substring(1, clz.getSimpleName().lastIndexOf("Type")))
                    .toString();
            try {
                Method toCall = objectFactory.getClass().getMethod(methodName, clz);
                result = toCall.invoke(objectFactory, element);
            } catch (ClassCastException | NoSuchMethodException | IllegalAccessException | IllegalAccessError | InvocationTargetException | IllegalArgumentException e) {
                throw new IllegalArgumentException("Unable to create JAXBElement wrapper", e);
            }
        } else {
            throw new IllegalArgumentException("Unable to find ObjectFactory for JAXB element type " + clz);
        }
        if (consumer != null) {
            consumer.accept(element);
        }
        return (JAXBElement<T>) result;
    }
    public static <T> Optional<T> resolveProperty(final Object prop, final Class<T> clz, final ReferredObjectRetrievalContext refCtx) {
        return resolveProperty(prop, null, clz, refCtx);
    }

    @SuppressWarnings("unchecked")
    public static <T> Optional<T> resolveProperty(final Object prop, final String propertyName, final Class<T> clz, final ReferredObjectRetrievalContext refCtx) {
        if (prop == null) {
            return Optional.empty();
        }
        try {
            //First try resolving the href reference (if it exists):
            try {
                Method getHref = prop.getClass().getMethod("getHref", (Class<?>[]) null);
                if (String.class.isAssignableFrom(getHref.getReturnType())) {
                    String id = (String) getHref.invoke(prop, (Object[]) null);
                    if (id != null) {
                        if (id.startsWith("#")) {
                            id = id.substring(1);
                        }
                        return refCtx.getReferredObject(id, clz);
                    }
                }
            } catch (NoSuchMethodException nsme) {
                //NOOP
            }

            //Then try to return embedded property value:
            String getterCandidate = null;
            if (propertyName != null) {
                getterCandidate = "get" + StringUtils.capitalize(propertyName);
            } else if (clz.getSimpleName().endsWith("Type")) {
                getterCandidate = "get" + clz.getSimpleName().substring(0, clz.getSimpleName().length() - 4);
            }
            if (getterCandidate != null) {
                Method getObject;
                try {
                    getObject = prop.getClass().getMethod(getterCandidate, (Class<?>[]) null);
                    if (clz.isAssignableFrom(getObject.getReturnType())) {
                        return (Optional<T>) Optional.ofNullable(getObject.invoke(prop, (Object[]) null));
                    } else if (JAXBElement.class.isAssignableFrom(getObject.getReturnType())) {
                        JAXBElement<?> wrapped = (JAXBElement<?>) getObject.invoke(prop, (Object[]) null);
                        Object value = wrapped.getValue();
                        if (value != null) {
                            if (clz.isAssignableFrom(value.getClass())) {
                                return (Optional<T>) Optional.of(value);
                            }
                        }
                    }
                } catch (NoSuchMethodException nsme) {
                    try {
                        getObject = prop.getClass().getMethod("getAny", (Class<?>[]) null);
                        Object wrapper = getObject.invoke(prop, (Object[]) null);
                        if (wrapper != null && JAXBElement.class.isAssignableFrom(wrapper.getClass())) {
                            Object value = ((JAXBElement)wrapper).getValue();
                            return (Optional<T>) Optional.of(value);
                        }
                    } catch (NoSuchMethodException nsme2) {
                        //NOOP
                    }
                }
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    protected static Optional<PartialOrCompleteTimePeriod> getCompleteTimePeriod(final TimeObjectPropertyType timeObjectPropertyType,
            final ReferredObjectRetrievalContext refCtx) {
        Optional<AbstractTimeObjectType> to = resolveProperty(timeObjectPropertyType, "abstractTimeObject", AbstractTimeObjectType.class, refCtx);
        if (to.isPresent()) {
            if (TimePeriodType.class.isAssignableFrom(to.get().getClass())) {
                TimePeriodType tp = (TimePeriodType) to.get();
                final PartialOrCompleteTimePeriod.Builder retval = new PartialOrCompleteTimePeriod.Builder();
                getStartTime(tp, refCtx).ifPresent((start) -> {
                    retval.setStartTime(new PartialOrCompleteTimeInstant.Builder()//
                            .setCompleteTime(start).build());
                });

                getEndTime(tp, refCtx).ifPresent((end) -> {
                    retval.setEndTime(new PartialOrCompleteTimeInstant.Builder()//
                            .setCompleteTime(end).build());
                });
                return Optional.of(retval.build());
            } else {
                throw new IllegalArgumentException("Time object is not a time period");
            }
        }
        return Optional.empty();
    }

    protected static Optional<PartialOrCompleteTimePeriod> getCompleteTimePeriod(final TimePeriodPropertyType timePeriodPropertyType,
            final ReferredObjectRetrievalContext refCtx) {
        Optional<TimePeriodType> tp = resolveProperty(timePeriodPropertyType, TimePeriodType.class, refCtx);
        if (tp.isPresent()) {
            final PartialOrCompleteTimePeriod.Builder retval = new PartialOrCompleteTimePeriod.Builder();
            getStartTime(tp.get(), refCtx).ifPresent((start) -> {
                retval.setStartTime(new PartialOrCompleteTimeInstant.Builder()//
                        .setCompleteTime(start).build());
            });

            getEndTime(tp.get(), refCtx).ifPresent((end) -> {
                retval.setEndTime(new PartialOrCompleteTimeInstant.Builder()//
                        .setCompleteTime(end).build());
            });
            return Optional.of(retval.build());
        }
        return Optional.empty();
    }

    protected static Optional<PartialOrCompleteTimeInstant> getCompleteTimeInstant(final TimeObjectPropertyType timeObjectPropertyType,
            final ReferredObjectRetrievalContext refCtx) {
        Optional<AbstractTimeObjectType> to = resolveProperty(timeObjectPropertyType, "abstractTimeObject", AbstractTimeObjectType.class, refCtx);
        if (to.isPresent()) {
            if (TimeInstantType.class.isAssignableFrom(to.get().getClass())) {
                TimeInstantType ti = (TimeInstantType) to.get();
                Optional<ZonedDateTime> time = getTime(ti.getTimePosition());
                if (time.isPresent()) {
                    return Optional.of(new PartialOrCompleteTimeInstant.Builder().setCompleteTime(time).build());
                }
            } else {
                throw new IllegalArgumentException("Time object is not a time instant");
            }
        }
        return Optional.empty();
    }
    protected static Optional<PartialOrCompleteTimeInstant> getCompleteTimeInstant(final TimeInstantPropertyType timeInstantPropertyType,
            final ReferredObjectRetrievalContext refCtx) {
        Optional<ZonedDateTime> time = getTime(timeInstantPropertyType, refCtx);
        if (time.isPresent()) {
            return Optional.of(new PartialOrCompleteTimeInstant.Builder().setCompleteTime(time.get()).build());
        }
        return Optional.empty();
    }

    protected static Optional<ZonedDateTime> getStartTime(final TimePeriodType period, final ReferredObjectRetrievalContext ctx) {
        Optional<ZonedDateTime> retval = Optional.empty();
        if (period.getBegin() != null) {
            retval = getTime(period.getBegin(), ctx);
        } else if (period.getBeginPosition() != null) {
            retval = getTime(period.getBeginPosition());
        }
        return retval;
    }

    protected static Optional<ZonedDateTime> getEndTime(final TimePeriodType period, final ReferredObjectRetrievalContext ctx) {
        Optional<ZonedDateTime> retval = Optional.empty();
        if (period.getEnd() != null) {
            retval = getTime(period.getEnd(), ctx);
        } else if (period.getEndPosition() != null) {
            retval = getTime(period.getEndPosition());
        }
        return retval;
    }

    protected static Optional<ZonedDateTime> getTime(final TimeInstantPropertyType tiProp, final ReferredObjectRetrievalContext ctx) {
        Optional<TimeInstantType> ti = resolveProperty(tiProp, TimeInstantType.class, ctx);
        if (ti.isPresent()) {
            return getTime(ti.get().getTimePosition());
        } else {
            return Optional.empty();
        }
    }

    protected static Optional<ZonedDateTime> getTime(final TimePositionType tp) {
        if (tp != null && tp.getValue() != null && !tp.getValue().isEmpty()) {
            return Optional.of(ZonedDateTime.parse(tp.getValue().get(0), DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        } else {
            return Optional.empty();
        }
    }


    private static Object getObjectFactory(Class<?> clz) {
        Object objectFactory = null;
        try {
            synchronized (objectFactoryMap) {

                objectFactory = classToObjectFactory.get(clz.getCanonicalName());
                if (objectFactory == null) {
                    String objectFactoryPath = clz.getPackage().getName();
                    String objectFactoryName = null;
                    Class<?> ofClass = null;
                    while (objectFactory == null && objectFactoryPath != null) {
                        objectFactoryName = objectFactoryPath + ".ObjectFactory";
                        objectFactory = objectFactoryMap.get(objectFactoryName);
                        if (objectFactory == null) {
                            try {
                                ofClass = IWXXMConverterBase.class.getClassLoader().loadClass(objectFactoryName);
                                break;
                            } catch (ClassNotFoundException cnfe) {
                                int nextDot = objectFactoryPath.lastIndexOf('.');
                                if (nextDot == -1) {
                                    objectFactoryPath = null;
                                } else {
                                    objectFactoryPath = objectFactoryPath.substring(0, nextDot);
                                }
                            }
                        }
                    }
                    if (ofClass != null) {
                        Constructor<?> c = ofClass.getConstructor();
                        objectFactory = c.newInstance();
                        objectFactoryMap.put(objectFactoryName, objectFactory);
                    }
                    classToObjectFactory.put(clz.getCanonicalName(), objectFactory);
                }
            }
            return objectFactory;
        } catch (ClassCastException | NoSuchMethodException | IllegalAccessException | IllegalAccessError | InstantiationException | InvocationTargetException e) {
            throw new IllegalArgumentException("Unable to get ObjectFactory for " + clz.getCanonicalName(), e);
        }
    }

}
