/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.action.get;

import com.google.common.collect.Iterators;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Streamable;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentBuilderString;

import java.io.IOException;
import java.util.Iterator;

public class MultiGetResponse implements ActionResponse, Iterable<MultiGetItemResponse>, ToXContent {

    /**
     * Represents a failure.
     */
    public static class Failure implements Streamable {
        private String index;
        private String type;
        private String id;
        private String message;

        Failure() {

        }

        public Failure(String index, String type, String id, String message) {
            this.index = index;
            this.type = type;
            this.id = id;
            this.message = message;
        }

        /**
         * The index name of the action.
         */
        public String index() {
            return this.index;
        }

        /**
         * The index name of the action.
         */
        public String getIndex() {
            return index();
        }

        /**
         * The type of the action.
         */
        public String type() {
            return type;
        }

        /**
         * The type of the action.
         */
        public String getType() {
            return type();
        }

        /**
         * The id of the action.
         */
        public String id() {
            return id;
        }

        /**
         * The id of the action.
         */
        public String getId() {
            return this.id;
        }

        /**
         * The failure message.
         */
        public String message() {
            return this.message;
        }

        /**
         * The failure message.
         */
        public String getMessage() {
            return message();
        }

        public static Failure readFailure(StreamInput in) throws IOException {
            Failure failure = new Failure();
            failure.readFrom(in);
            return failure;
        }

        @Override
        public void readFrom(StreamInput in) throws IOException {
            index = in.readUTF();
            if (in.readBoolean()) {
                type = in.readUTF();
            }
            id = in.readUTF();
            message = in.readUTF();
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeUTF(index);
            if (type == null) {
                out.writeBoolean(false);
            } else {
                out.writeBoolean(true);
                out.writeUTF(type);
            }
            out.writeUTF(id);
            out.writeUTF(message);
        }
    }

    private MultiGetItemResponse[] responses;

    MultiGetResponse() {
    }

    public MultiGetResponse(MultiGetItemResponse[] responses) {
        this.responses = responses;
    }

    public MultiGetItemResponse[] responses() {
        return this.responses;
    }

    @Override
    public Iterator<MultiGetItemResponse> iterator() {
        return Iterators.forArray(responses);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.startArray(Fields.DOCS);
        for (MultiGetItemResponse response : responses) {
            if (response.failed()) {
                builder.startObject();
                Failure failure = response.failure();
                builder.field(Fields._INDEX, failure.index());
                builder.field(Fields._TYPE, failure.type());
                builder.field(Fields._ID, failure.id());
                builder.field(Fields.ERROR, failure.message());
                builder.endObject();
            } else {
                GetResponse getResponse = response.getResponse();
                getResponse.toXContent(builder, params);
            }
        }
        builder.endArray();
        builder.endObject();
        return builder;
    }

    static final class Fields {
        static final XContentBuilderString DOCS = new XContentBuilderString("docs");
        static final XContentBuilderString _INDEX = new XContentBuilderString("_index");
        static final XContentBuilderString _TYPE = new XContentBuilderString("_type");
        static final XContentBuilderString _ID = new XContentBuilderString("_id");
        static final XContentBuilderString ERROR = new XContentBuilderString("error");
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        responses = new MultiGetItemResponse[in.readVInt()];
        for (int i = 0; i < responses.length; i++) {
            responses[i] = MultiGetItemResponse.readItemResponse(in);
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeVInt(responses.length);
        for (MultiGetItemResponse response : responses) {
            response.writeTo(out);
        }
    }
}