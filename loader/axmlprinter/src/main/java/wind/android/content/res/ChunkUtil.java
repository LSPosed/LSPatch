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
package wind.android.content.res;

import wind.android.content.res.IntReader;

import java.io.IOException;

/**
 * @author Dmitry Skiba
 * 
 */
class ChunkUtil {

	public static final void readCheckType(IntReader reader, int expectedType) throws IOException {
		int type=reader.readInt();
		if (type!=expectedType) {
			throw new IOException(
				"Expected chunk of type 0x"+Integer.toHexString(expectedType)+
				", read 0x"+Integer.toHexString(type)+".");
		}
	}
}
