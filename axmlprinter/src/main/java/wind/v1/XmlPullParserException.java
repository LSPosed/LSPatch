/* -*-             c-basic-offset: 4; indent-tabs-mode: nil; -*-  //------100-columns-wide------>|*/
// for license please see accompanying LICENSE.txt file (available also at http://www.xmlpull.org/)

package wind.v1;

import wind.v1.XmlPullParser;

/**
 * This exception is thrown to signal XML Pull Parser related faults.
 *
 * @author <a href="http://www.extreme.indiana.edu/~aslom/">Aleksander Slominski</a>
 */
public class XmlPullParserException extends Exception {
    protected Throwable detail;
    protected int row = -1;
    protected int column = -1;

    /*    public XmlPullParserException() {
          }*/

    public XmlPullParserException(String s) {
        super(s);
    }

    /*
    public XmlPullParserException(String s, Throwable thrwble) {
        super(s);
        this.detail = thrwble;
        }

    public XmlPullParserException(String s, int row, int column) {
        super(s);
        this.row = row;
        this.column = column;
    }
    */

    public XmlPullParserException(String msg, XmlPullParser parser, Throwable chain) {
        super ((msg == null ? "" : msg+" ")
               + (parser == null ? "" : "(position:"+parser.getPositionDescription()+") ")
               + (chain == null ? "" : "caused by: "+chain));

        if (parser != null) {
            this.row = parser.getLineNumber();
            this.column = parser.getColumnNumber();
        }
        this.detail = chain;
    }

    public Throwable getDetail() { return detail; }
    //    public void setDetail(Throwable cause) { this.detail = cause; }
    public int getLineNumber() { return row; }
    public int getColumnNumber() { return column; }

    /*
    public String getMessage() {
        if(detail == null)
            return super.getMessage();
        else
            return super.getMessage() + "; nested exception is: \n\t"
                + detail.getMessage();
    }
    */

    //NOTE: code that prints this and detail is difficult in J2ME
    public void printStackTrace() {
        if (detail == null) {
            super.printStackTrace();
        } else {
            synchronized(System.err) {
                System.err.println(super.getMessage() + "; nested exception is:");
                detail.printStackTrace();
            }
        }
    }

}

