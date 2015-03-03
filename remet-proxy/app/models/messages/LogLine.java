/**
 * Copyright 2014 Groupon.com
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

package models.messages;

import com.google.common.base.MoreObjects;

import java.nio.file.Path;

/**
 * Message class to hold data about a log entry that should be sent to clients.
 *
 * @author Mohammed Kamel (mkamel at groupon dot com)
 * @author Vivek Muppala (vivek at groupon dot com)
 */
public class LogLine {
    /**
     * Public constructor.
     *
     * @param file The name of the log file
     * @param line The raw log line.
     */
    public LogLine(
            final Path file,
            final byte[] line) {
        _file = file;
        _line = line;
    }

    public Path getFile() {
        return _file;
    }

    public byte[] getLine() {
        return _line;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", Integer.toHexString(System.identityHashCode(this)))
                .add("File", _file)
                .add("Line", _line)
                .toString();
    }

    private final Path _file;
    private final byte[] _line;
}