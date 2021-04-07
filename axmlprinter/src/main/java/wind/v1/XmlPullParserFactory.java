/* -*-             c-basic-offset: 4; indent-tabs-mode: nil; -*-  //------100-columns-wide------>|*/
// for license please see accompanying LICENSE.txt file (available also at http://www.xmlpull.org/)

package wind.v1;

import wind.v1.XmlPullParser;
import wind.v1.XmlPullParserException;
import wind.v1.XmlSerializer;

import java.io.InputStream;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

/**
 * This class is used to create implementations of XML Pull Parser defined in XMPULL V1 API.
 * The name of actual factory class will be determined based on several parameters.
 * It works similar to JAXP but tailored to work in J2ME environments
 * (no access to system properties or file system) so name of parser class factory to use
 * and its class used for loading (no class loader - on J2ME no access to context class loaders)
 * must be passed explicitly. If no name of parser factory was passed (or is null)
 * it will try to find name by searching in CLASSPATH for
 * META-INF/services/wind.v1.XmlPullParserFactory resource that should contain
 * a comma separated list of class names of factories or parsers to try (in order from
 * left to the right). If none found, it will throw an exception.
 *
 * <br /><strong>NOTE:</strong>In J2SE or J2EE environments, you may want to use
 * <code>newInstance(property, classLoaderCtx)</code>
 * where first argument is
 * <code>System.getProperty(XmlPullParserFactory.PROPERTY_NAME)</code>
 * and second is <code>Thread.getContextClassLoader().getClass()</code> .
 *
 * @see wind.v1.XmlPullParser
 *
 * @author <a href="http://www.extreme.indiana.edu/~aslom/">Aleksander Slominski</a>
 * @author Stefan Haustein
 */

public class XmlPullParserFactory {
    /** used as default class to server as context class in newInstance() */
    final static Class referenceContextClass;

    static {
        XmlPullParserFactory f = new XmlPullParserFactory();
        referenceContextClass = f.getClass();
    }

    /** Name of the system or midlet property that should be used for
     a system property containing a comma separated list of factory
     or parser class names (value:
     wind.v1.XmlPullParserFactory). */


    public static final String PROPERTY_NAME =
        "org.xmlpull.v1.XmlPullParserFactory";

    private static final String RESOURCE_NAME =
        "/META-INF/services/" + PROPERTY_NAME;


    // public static final String DEFAULT_PROPERTY =
    //    "wind.xpp3.XmlPullParser,org.kxml2.io.KXmlParser";


    protected Vector parserClasses;
    protected String classNamesLocation;

    protected Vector serializerClasses;


    // features are kept there
    protected Hashtable features = new Hashtable();


    /**
     * Protected constructor to be called by factory implementations.
     */

    protected XmlPullParserFactory() {
    }



    /**
     * Set the features to be set when XML Pull Parser is created by this factory.
     * <p><b>NOTE:</b> factory features are not used for XML Serializer.
     *
     * @param name string with URI identifying feature
     * @param state if true feature will be set; if false will be ignored
     */

    public void setFeature(String name,
                           boolean state) throws XmlPullParserException {

        features.put(name, new Boolean(state));
    }


    /**
     * Return the current value of the feature with given name.
     * <p><b>NOTE:</b> factory features are not used for XML Serializer.
     *
     * @param name The name of feature to be retrieved.
     * @return The value of named feature.
     *     Unknown features are <string>always</strong> returned as false
     */

    public boolean getFeature (String name) {
        Boolean value = (Boolean) features.get(name);
        return value != null ? value.booleanValue() : false;
    }

    /**
     * Specifies that the parser produced by this factory will provide
     * support for XML namespaces.
     * By default the value of this is set to false.
     *
     * @param awareness true if the parser produced by this code
     *    will provide support for XML namespaces;  false otherwise.
     */

    public void setNamespaceAware(boolean awareness) {
        features.put (wind.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, new Boolean (awareness));
    }

    /**
     * Indicates whether or not the factory is configured to produce
     * parsers which are namespace aware
     * (it simply set feature XmlPullParser.FEATURE_PROCESS_NAMESPACES to true or false).
     *
     * @return  true if the factory is configured to produce parsers
     *    which are namespace aware; false otherwise.
     */

    public boolean isNamespaceAware() {
        return getFeature (wind.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES);
    }


    /**
     * Specifies that the parser produced by this factory will be validating
     * (it simply set feature XmlPullParser.FEATURE_VALIDATION to true or false).
     *
     * By default the value of this is set to false.
     *
     * @param validating - if true the parsers created by this factory  must be validating.
     */

    public void setValidating(boolean validating) {
        features.put (wind.v1.XmlPullParser.FEATURE_VALIDATION, new Boolean (validating));
    }

    /**
     * Indicates whether or not the factory is configured to produce parsers
     * which validate the XML content during parse.
     *
     * @return   true if the factory is configured to produce parsers
     * which validate the XML content during parse; false otherwise.
     */

    public boolean isValidating() {
        return getFeature (wind.v1.XmlPullParser.FEATURE_VALIDATION);
    }

    /**
     * Creates a new instance of a XML Pull Parser
     * using the currently configured factory features.
     *
     * @return A new instance of a XML Pull Parser.
     * @throws XmlPullParserException if a parser cannot be created which satisfies the
     * requested configuration.
     */

    public wind.v1.XmlPullParser newPullParser() throws XmlPullParserException {

        if (parserClasses == null) throw new XmlPullParserException
                ("Factory initialization was incomplete - has not tried "+classNamesLocation);

        if (parserClasses.size() == 0) throw new XmlPullParserException
                ("No valid parser classes found in "+classNamesLocation);

        final StringBuffer issues = new StringBuffer ();

        for (int i = 0; i < parserClasses.size (); i++) {
            final Class ppClass = (Class) parserClasses.elementAt (i);
            try {
                final wind.v1.XmlPullParser pp = (wind.v1.XmlPullParser) ppClass.newInstance();
                //            if( ! features.isEmpty() ) {
                //Enumeration keys = features.keys();
                // while(keys.hasMoreElements()) {

                for (Enumeration e = features.keys (); e.hasMoreElements ();) {
                    final String key = (String) e.nextElement();
                    final Boolean value = (Boolean) features.get(key);
                    if(value != null && value.booleanValue()) {
                        pp.setFeature(key, true);
                    }
                }
                return pp;

            } catch(Exception ex) {
                issues.append (ppClass.getName () + ": "+ ex.toString ()+"; ");
            }
        }

        throw new XmlPullParserException ("could not create parser: "+issues);
    }


    /**
     * Creates a new instance of a XML Serializer.
     *
     * <p><b>NOTE:</b> factory features are not used for XML Serializer.
     *
     * @return A new instance of a XML Serializer.
     * @throws XmlPullParserException if a parser cannot be created which satisfies the
     * requested configuration.
     */

    public wind.v1.XmlSerializer newSerializer() throws XmlPullParserException {

        if (serializerClasses == null) {
            throw new XmlPullParserException
                ("Factory initialization incomplete - has not tried "+classNamesLocation);
        }
        if(serializerClasses.size() == 0) {
            throw new XmlPullParserException
                ("No valid serializer classes found in "+classNamesLocation);
        }

        final StringBuffer issues = new StringBuffer ();

        for (int i = 0; i < serializerClasses.size (); i++) {
            final Class ppClass = (Class) serializerClasses.elementAt (i);
            try {
                final wind.v1.XmlSerializer ser = (wind.v1.XmlSerializer) ppClass.newInstance();

                //                for (Enumeration e = features.keys (); e.hasMoreElements ();) {
                //                    String key = (String) e.nextElement();
                //                    Boolean value = (Boolean) features.get(key);
                //                    if(value != null && value.booleanValue()) {
                //                        ser.setFeature(key, true);
                //                    }
                //                }
                return ser;

            } catch(Exception ex) {
                issues.append (ppClass.getName () + ": "+ ex.toString ()+"; ");
            }
        }

        throw new XmlPullParserException ("could not create serializer: "+issues);
    }

    /**
     * Create a new instance of a PullParserFactory that can be used
     * to create XML pull parsers (see class description for more
     * details).
     *
     * @return a new instance of a PullParserFactory, as returned by newInstance (null, null); 
     */
    public static XmlPullParserFactory newInstance () throws XmlPullParserException {
        return newInstance(null, null);
    }

    public static XmlPullParserFactory newInstance (String classNames, Class context)
        throws XmlPullParserException {

        if (context == null) {
            //NOTE: make sure context uses the same class loader as API classes
            //      this is the best we can do without having access to context classloader in J2ME
            //      if API is in the same classloader as implementation then this will work
            context = referenceContextClass;
        }

        String  classNamesLocation = null;

        if (classNames == null || classNames.length() == 0 || "DEFAULT".equals(classNames)) {
            try {
                InputStream is = context.getResourceAsStream (RESOURCE_NAME);

                if (is == null) throw new XmlPullParserException
                        ("resource not found: "+RESOURCE_NAME
                             +" make sure that parser implementing XmlPull API is available");
                final StringBuffer sb = new StringBuffer();

                while (true) {
                    final int ch = is.read();
                    if (ch < 0) break;
                    else if (ch > ' ')
                        sb.append((char) ch);
                }
                is.close ();

                classNames = sb.toString ();
            }
            catch (Exception e) {
                throw new XmlPullParserException (null, null, e);
            }
            classNamesLocation = "resource "+RESOURCE_NAME+" that contained '"+classNames+"'";
        } else {
            classNamesLocation =
                "parameter classNames to newInstance() that contained '"+classNames+"'";
        }

        XmlPullParserFactory factory = null;
        final Vector parserClasses = new Vector ();
        final Vector serializerClasses = new Vector ();
        int pos = 0;

        while (pos < classNames.length ()) {
            int cut = classNames.indexOf (',', pos);

            if (cut == -1) cut = classNames.length ();
            final String name = classNames.substring (pos, cut);

            Class candidate = null;
            Object instance = null;

            try {
                candidate = Class.forName (name);
                // necessary because of J2ME .class issue
                instance = candidate.newInstance ();
            }
            catch (Exception e) {}

            if (candidate != null) {
                boolean recognized = false;
                if (instance instanceof XmlPullParser) {
                    parserClasses.addElement (candidate);
                    recognized = true;
                }
                if (instance instanceof XmlSerializer) {
                    serializerClasses.addElement (candidate);
                    recognized = true;
                }
                if (instance instanceof XmlPullParserFactory) {
                    if (factory == null) {
                        factory = (XmlPullParserFactory) instance;
                    }
                    recognized = true;
                }
                if (!recognized) {
                    throw new XmlPullParserException ("incompatible class: "+name);
                }
            }
            pos = cut + 1;
        }

        if (factory == null) {
            factory = new XmlPullParserFactory ();
        }
        factory.parserClasses = parserClasses;
        factory.serializerClasses = serializerClasses;
        factory.classNamesLocation = classNamesLocation;
        return factory;
    }
}


