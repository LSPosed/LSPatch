/*
 * Copyright 2008 Android4ME
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package wind.android.util;

/**
 * @author Dmitry Skiba
 *
 */
public class TypedValue {

    public int type;
    public CharSequence string;
    public int data;
    public int assetCookie;
    public int resourceId;
    public int changingConfigurations;
	
    public static final int 
    	TYPE_NULL				=0,
    	TYPE_REFERENCE			=1,
    	TYPE_ATTRIBUTE			=2,
    	TYPE_STRING				=3,
    	TYPE_FLOAT				=4,
    	TYPE_DIMENSION			=5,
    	TYPE_FRACTION			=6,
    	TYPE_FIRST_INT			=16,
    	TYPE_INT_DEC			=16,
    	TYPE_INT_HEX			=17,
    	TYPE_INT_BOOLEAN		=18,
    	TYPE_FIRST_COLOR_INT	=28,
    	TYPE_INT_COLOR_ARGB8	=28,
    	TYPE_INT_COLOR_RGB8		=29,
    	TYPE_INT_COLOR_ARGB4	=30,
    	TYPE_INT_COLOR_RGB4		=31,
    	TYPE_LAST_COLOR_INT		=31,
    	TYPE_LAST_INT			=31;
    
    public static final int
	    COMPLEX_UNIT_PX			=0,
	    COMPLEX_UNIT_DIP		=1,
	    COMPLEX_UNIT_SP			=2,
	    COMPLEX_UNIT_PT			=3,
	    COMPLEX_UNIT_IN			=4,
	    COMPLEX_UNIT_MM			=5,
    	COMPLEX_UNIT_SHIFT		=0,
	    COMPLEX_UNIT_MASK		=15,
	    COMPLEX_UNIT_FRACTION	=0,
	    COMPLEX_UNIT_FRACTION_PARENT=1,
	    COMPLEX_RADIX_23p0		=0,
	    COMPLEX_RADIX_16p7		=1,
	    COMPLEX_RADIX_8p15		=2,
	    COMPLEX_RADIX_0p23		=3,
	    COMPLEX_RADIX_SHIFT		=4,
	    COMPLEX_RADIX_MASK		=3,
	    COMPLEX_MANTISSA_SHIFT	=8,
	    COMPLEX_MANTISSA_MASK	=0xFFFFFF;
	
}
