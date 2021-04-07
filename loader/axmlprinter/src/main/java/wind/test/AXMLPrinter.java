/*
 * Copyright 2008 Android4ME
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	 http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package wind.test;

import java.io.FileInputStream;
import wind.v1.XmlPullParser;
import wind.android.content.res.AXmlResourceParser;
//import android.util.TypedValue;

/**
 * @author Dmitry Skiba
 * 
 * This is example usage of AXMLParser class.
 * 
 * Prints xml document from Android's binary xml file.
 */
public class AXMLPrinter {

	public static void main(String[] arguments) {
		if (arguments.length<1) {
			log("Usage: AXMLPrinter <binary xml file>");
			return;
		}
		try {
			AXmlResourceParser parser=new AXmlResourceParser();
			parser.open(new FileInputStream(arguments[0]));
			StringBuilder indent=new StringBuilder(10);
			final String indentStep="	";
			while (true) {
				int type=parser.next();
				if (type==XmlPullParser.END_DOCUMENT) {
					break;
				}
				switch (type) {
					case XmlPullParser.START_DOCUMENT:
					{
						log("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
						break;
					}
					case XmlPullParser.START_TAG:
					{
						log("%s<%s%s",indent,
							getNamespacePrefix(parser.getPrefix()),parser.getName());
						indent.append(indentStep);
						
						int namespaceCountBefore=parser.getNamespaceCount(parser.getDepth()-1);
						int namespaceCount=parser.getNamespaceCount(parser.getDepth());
						for (int i=namespaceCountBefore;i!=namespaceCount;++i) {
							log("%sxmlns:%s=\"%s\"",
								indent,
								parser.getNamespacePrefix(i),
								parser.getNamespaceUri(i));
						}
						
						for (int i=0;i!=parser.getAttributeCount();++i) {
							log("%s%s%s=\"%s\"",indent,
								getNamespacePrefix(parser.getAttributePrefix(i)),
								parser.getAttributeName(i),
								getAttributeValue(parser,i));
						}
						log("%s>",indent);
						break;
					}
					case XmlPullParser.END_TAG:
					{
						indent.setLength(indent.length()-indentStep.length());
						log("%s</%s%s>",indent,
							getNamespacePrefix(parser.getPrefix()),
							parser.getName());
						break;
					}
					case XmlPullParser.TEXT:
					{
						log("%s%s",indent,parser.getText());
						break;
					}
				}
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private static String getNamespacePrefix(String prefix) {
		if (prefix==null || prefix.length()==0) {
			return "";
		}
		return prefix+":";
	}
	
	private static String getAttributeValue(AXmlResourceParser parser,int index) {
		int type=parser.getAttributeValueType(index);
		int data=parser.getAttributeValueData(index);
//		if (type==TypedValue.TYPE_STRING) {
//			return parser.getAttributeValue(index);
//		}
//		if (type==TypedValue.TYPE_ATTRIBUTE) {
//			return String.format("?%s%08X",getPackage(data),data);
//		}
//		if (type==TypedValue.TYPE_REFERENCE) {
//			return String.format("@%s%08X",getPackage(data),data);
//		}
//		if (type==TypedValue.TYPE_FLOAT) {
//			return String.valueOf(Float.intBitsToFloat(data));
//		}
//		if (type==TypedValue.TYPE_INT_HEX) {
//			return String.format("0x%08X",data);
//		}
//		if (type==TypedValue.TYPE_INT_BOOLEAN) {
//			return data!=0?"true":"false";
//		}
//		if (type==TypedValue.TYPE_DIMENSION) {
//			return Float.toString(complexToFloat(data))+
//				DIMENSION_UNITS[data & TypedValue.COMPLEX_UNIT_MASK];
//		}
//		if (type==TypedValue.TYPE_FRACTION) {
//			return Float.toString(complexToFloat(data))+
//				FRACTION_UNITS[data & TypedValue.COMPLEX_UNIT_MASK];
//		}
//		if (type>=TypedValue.TYPE_FIRST_COLOR_INT && type<=TypedValue.TYPE_LAST_COLOR_INT) {
//			return String.format("#%08X",data);
//		}
//		if (type>=TypedValue.TYPE_FIRST_INT && type<=TypedValue.TYPE_LAST_INT) {
//			return String.valueOf(data);
//		}
		return String.format("<0x%X, type 0x%02X>",data,type);
	}
	
	private static String getPackage(int id) {
		if (id>>>24==1) {
			return "android:";
		}
		return "";
	}

	private static void log(String format,Object...arguments) {
		System.out.printf(format,arguments);
		System.out.println();
	}
	
	/////////////////////////////////// ILLEGAL STUFF, DONT LOOK :)
	
	public static float complexToFloat(int complex) {
		return (float)(complex & 0xFFFFFF00)*RADIX_MULTS[(complex>>4) & 3];
	}
	
	private static final float RADIX_MULTS[]={
		0.00390625F,3.051758E-005F,1.192093E-007F,4.656613E-010F
	};
	private static final String DIMENSION_UNITS[]={
		"px","dip","sp","pt","in","mm","",""
	};
	private static final String FRACTION_UNITS[]={
		"%","%p","","","","","",""
	};
}