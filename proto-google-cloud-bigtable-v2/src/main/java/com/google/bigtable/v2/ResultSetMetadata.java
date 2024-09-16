/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: google/bigtable/v2/data.proto

// Protobuf Java Version: 3.25.4
package com.google.bigtable.v2;

/**
 *
 *
 * <pre>
 * Describes the structure of a Bigtable result set.
 * </pre>
 *
 * Protobuf type {@code google.bigtable.v2.ResultSetMetadata}
 */
public final class ResultSetMetadata extends com.google.protobuf.GeneratedMessageV3
    implements
    // @@protoc_insertion_point(message_implements:google.bigtable.v2.ResultSetMetadata)
    ResultSetMetadataOrBuilder {
  private static final long serialVersionUID = 0L;
  // Use ResultSetMetadata.newBuilder() to construct.
  private ResultSetMetadata(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
    super(builder);
  }

  private ResultSetMetadata() {}

  @java.lang.Override
  @SuppressWarnings({"unused"})
  protected java.lang.Object newInstance(UnusedPrivateParameter unused) {
    return new ResultSetMetadata();
  }

  public static final com.google.protobuf.Descriptors.Descriptor getDescriptor() {
    return com.google.bigtable.v2.DataProto
        .internal_static_google_bigtable_v2_ResultSetMetadata_descriptor;
  }

  @java.lang.Override
  protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internalGetFieldAccessorTable() {
    return com.google.bigtable.v2.DataProto
        .internal_static_google_bigtable_v2_ResultSetMetadata_fieldAccessorTable
        .ensureFieldAccessorsInitialized(
            com.google.bigtable.v2.ResultSetMetadata.class,
            com.google.bigtable.v2.ResultSetMetadata.Builder.class);
  }

  private int schemaCase_ = 0;

  @SuppressWarnings("serial")
  private java.lang.Object schema_;

  public enum SchemaCase
      implements
          com.google.protobuf.Internal.EnumLite,
          com.google.protobuf.AbstractMessage.InternalOneOfEnum {
    PROTO_SCHEMA(1),
    SCHEMA_NOT_SET(0);
    private final int value;

    private SchemaCase(int value) {
      this.value = value;
    }
    /**
     * @param value The number of the enum to look for.
     * @return The enum associated with the given number.
     * @deprecated Use {@link #forNumber(int)} instead.
     */
    @java.lang.Deprecated
    public static SchemaCase valueOf(int value) {
      return forNumber(value);
    }

    public static SchemaCase forNumber(int value) {
      switch (value) {
        case 1:
          return PROTO_SCHEMA;
        case 0:
          return SCHEMA_NOT_SET;
        default:
          return null;
      }
    }

    public int getNumber() {
      return this.value;
    }
  };

  public SchemaCase getSchemaCase() {
    return SchemaCase.forNumber(schemaCase_);
  }

  public static final int PROTO_SCHEMA_FIELD_NUMBER = 1;
  /**
   *
   *
   * <pre>
   * Schema in proto format
   * </pre>
   *
   * <code>.google.bigtable.v2.ProtoSchema proto_schema = 1;</code>
   *
   * @return Whether the protoSchema field is set.
   */
  @java.lang.Override
  public boolean hasProtoSchema() {
    return schemaCase_ == 1;
  }
  /**
   *
   *
   * <pre>
   * Schema in proto format
   * </pre>
   *
   * <code>.google.bigtable.v2.ProtoSchema proto_schema = 1;</code>
   *
   * @return The protoSchema.
   */
  @java.lang.Override
  public com.google.bigtable.v2.ProtoSchema getProtoSchema() {
    if (schemaCase_ == 1) {
      return (com.google.bigtable.v2.ProtoSchema) schema_;
    }
    return com.google.bigtable.v2.ProtoSchema.getDefaultInstance();
  }
  /**
   *
   *
   * <pre>
   * Schema in proto format
   * </pre>
   *
   * <code>.google.bigtable.v2.ProtoSchema proto_schema = 1;</code>
   */
  @java.lang.Override
  public com.google.bigtable.v2.ProtoSchemaOrBuilder getProtoSchemaOrBuilder() {
    if (schemaCase_ == 1) {
      return (com.google.bigtable.v2.ProtoSchema) schema_;
    }
    return com.google.bigtable.v2.ProtoSchema.getDefaultInstance();
  }

  private byte memoizedIsInitialized = -1;

  @java.lang.Override
  public final boolean isInitialized() {
    byte isInitialized = memoizedIsInitialized;
    if (isInitialized == 1) return true;
    if (isInitialized == 0) return false;

    memoizedIsInitialized = 1;
    return true;
  }

  @java.lang.Override
  public void writeTo(com.google.protobuf.CodedOutputStream output) throws java.io.IOException {
    if (schemaCase_ == 1) {
      output.writeMessage(1, (com.google.bigtable.v2.ProtoSchema) schema_);
    }
    getUnknownFields().writeTo(output);
  }

  @java.lang.Override
  public int getSerializedSize() {
    int size = memoizedSize;
    if (size != -1) return size;

    size = 0;
    if (schemaCase_ == 1) {
      size +=
          com.google.protobuf.CodedOutputStream.computeMessageSize(
              1, (com.google.bigtable.v2.ProtoSchema) schema_);
    }
    size += getUnknownFields().getSerializedSize();
    memoizedSize = size;
    return size;
  }

  @java.lang.Override
  public boolean equals(final java.lang.Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof com.google.bigtable.v2.ResultSetMetadata)) {
      return super.equals(obj);
    }
    com.google.bigtable.v2.ResultSetMetadata other = (com.google.bigtable.v2.ResultSetMetadata) obj;

    if (!getSchemaCase().equals(other.getSchemaCase())) return false;
    switch (schemaCase_) {
      case 1:
        if (!getProtoSchema().equals(other.getProtoSchema())) return false;
        break;
      case 0:
      default:
    }
    if (!getUnknownFields().equals(other.getUnknownFields())) return false;
    return true;
  }

  @java.lang.Override
  public int hashCode() {
    if (memoizedHashCode != 0) {
      return memoizedHashCode;
    }
    int hash = 41;
    hash = (19 * hash) + getDescriptor().hashCode();
    switch (schemaCase_) {
      case 1:
        hash = (37 * hash) + PROTO_SCHEMA_FIELD_NUMBER;
        hash = (53 * hash) + getProtoSchema().hashCode();
        break;
      case 0:
      default:
    }
    hash = (29 * hash) + getUnknownFields().hashCode();
    memoizedHashCode = hash;
    return hash;
  }

  public static com.google.bigtable.v2.ResultSetMetadata parseFrom(java.nio.ByteBuffer data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }

  public static com.google.bigtable.v2.ResultSetMetadata parseFrom(
      java.nio.ByteBuffer data, com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }

  public static com.google.bigtable.v2.ResultSetMetadata parseFrom(
      com.google.protobuf.ByteString data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }

  public static com.google.bigtable.v2.ResultSetMetadata parseFrom(
      com.google.protobuf.ByteString data,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }

  public static com.google.bigtable.v2.ResultSetMetadata parseFrom(byte[] data)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data);
  }

  public static com.google.bigtable.v2.ResultSetMetadata parseFrom(
      byte[] data, com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws com.google.protobuf.InvalidProtocolBufferException {
    return PARSER.parseFrom(data, extensionRegistry);
  }

  public static com.google.bigtable.v2.ResultSetMetadata parseFrom(java.io.InputStream input)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3.parseWithIOException(PARSER, input);
  }

  public static com.google.bigtable.v2.ResultSetMetadata parseFrom(
      java.io.InputStream input, com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3.parseWithIOException(
        PARSER, input, extensionRegistry);
  }

  public static com.google.bigtable.v2.ResultSetMetadata parseDelimitedFrom(
      java.io.InputStream input) throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3.parseDelimitedWithIOException(PARSER, input);
  }

  public static com.google.bigtable.v2.ResultSetMetadata parseDelimitedFrom(
      java.io.InputStream input, com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3.parseDelimitedWithIOException(
        PARSER, input, extensionRegistry);
  }

  public static com.google.bigtable.v2.ResultSetMetadata parseFrom(
      com.google.protobuf.CodedInputStream input) throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3.parseWithIOException(PARSER, input);
  }

  public static com.google.bigtable.v2.ResultSetMetadata parseFrom(
      com.google.protobuf.CodedInputStream input,
      com.google.protobuf.ExtensionRegistryLite extensionRegistry)
      throws java.io.IOException {
    return com.google.protobuf.GeneratedMessageV3.parseWithIOException(
        PARSER, input, extensionRegistry);
  }

  @java.lang.Override
  public Builder newBuilderForType() {
    return newBuilder();
  }

  public static Builder newBuilder() {
    return DEFAULT_INSTANCE.toBuilder();
  }

  public static Builder newBuilder(com.google.bigtable.v2.ResultSetMetadata prototype) {
    return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
  }

  @java.lang.Override
  public Builder toBuilder() {
    return this == DEFAULT_INSTANCE ? new Builder() : new Builder().mergeFrom(this);
  }

  @java.lang.Override
  protected Builder newBuilderForType(com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
    Builder builder = new Builder(parent);
    return builder;
  }
  /**
   *
   *
   * <pre>
   * Describes the structure of a Bigtable result set.
   * </pre>
   *
   * Protobuf type {@code google.bigtable.v2.ResultSetMetadata}
   */
  public static final class Builder extends com.google.protobuf.GeneratedMessageV3.Builder<Builder>
      implements
      // @@protoc_insertion_point(builder_implements:google.bigtable.v2.ResultSetMetadata)
      com.google.bigtable.v2.ResultSetMetadataOrBuilder {
    public static final com.google.protobuf.Descriptors.Descriptor getDescriptor() {
      return com.google.bigtable.v2.DataProto
          .internal_static_google_bigtable_v2_ResultSetMetadata_descriptor;
    }

    @java.lang.Override
    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return com.google.bigtable.v2.DataProto
          .internal_static_google_bigtable_v2_ResultSetMetadata_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              com.google.bigtable.v2.ResultSetMetadata.class,
              com.google.bigtable.v2.ResultSetMetadata.Builder.class);
    }

    // Construct using com.google.bigtable.v2.ResultSetMetadata.newBuilder()
    private Builder() {}

    private Builder(com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
      super(parent);
    }

    @java.lang.Override
    public Builder clear() {
      super.clear();
      bitField0_ = 0;
      if (protoSchemaBuilder_ != null) {
        protoSchemaBuilder_.clear();
      }
      schemaCase_ = 0;
      schema_ = null;
      return this;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.Descriptor getDescriptorForType() {
      return com.google.bigtable.v2.DataProto
          .internal_static_google_bigtable_v2_ResultSetMetadata_descriptor;
    }

    @java.lang.Override
    public com.google.bigtable.v2.ResultSetMetadata getDefaultInstanceForType() {
      return com.google.bigtable.v2.ResultSetMetadata.getDefaultInstance();
    }

    @java.lang.Override
    public com.google.bigtable.v2.ResultSetMetadata build() {
      com.google.bigtable.v2.ResultSetMetadata result = buildPartial();
      if (!result.isInitialized()) {
        throw newUninitializedMessageException(result);
      }
      return result;
    }

    @java.lang.Override
    public com.google.bigtable.v2.ResultSetMetadata buildPartial() {
      com.google.bigtable.v2.ResultSetMetadata result =
          new com.google.bigtable.v2.ResultSetMetadata(this);
      if (bitField0_ != 0) {
        buildPartial0(result);
      }
      buildPartialOneofs(result);
      onBuilt();
      return result;
    }

    private void buildPartial0(com.google.bigtable.v2.ResultSetMetadata result) {
      int from_bitField0_ = bitField0_;
    }

    private void buildPartialOneofs(com.google.bigtable.v2.ResultSetMetadata result) {
      result.schemaCase_ = schemaCase_;
      result.schema_ = this.schema_;
      if (schemaCase_ == 1 && protoSchemaBuilder_ != null) {
        result.schema_ = protoSchemaBuilder_.build();
      }
    }

    @java.lang.Override
    public Builder clone() {
      return super.clone();
    }

    @java.lang.Override
    public Builder setField(
        com.google.protobuf.Descriptors.FieldDescriptor field, java.lang.Object value) {
      return super.setField(field, value);
    }

    @java.lang.Override
    public Builder clearField(com.google.protobuf.Descriptors.FieldDescriptor field) {
      return super.clearField(field);
    }

    @java.lang.Override
    public Builder clearOneof(com.google.protobuf.Descriptors.OneofDescriptor oneof) {
      return super.clearOneof(oneof);
    }

    @java.lang.Override
    public Builder setRepeatedField(
        com.google.protobuf.Descriptors.FieldDescriptor field, int index, java.lang.Object value) {
      return super.setRepeatedField(field, index, value);
    }

    @java.lang.Override
    public Builder addRepeatedField(
        com.google.protobuf.Descriptors.FieldDescriptor field, java.lang.Object value) {
      return super.addRepeatedField(field, value);
    }

    @java.lang.Override
    public Builder mergeFrom(com.google.protobuf.Message other) {
      if (other instanceof com.google.bigtable.v2.ResultSetMetadata) {
        return mergeFrom((com.google.bigtable.v2.ResultSetMetadata) other);
      } else {
        super.mergeFrom(other);
        return this;
      }
    }

    public Builder mergeFrom(com.google.bigtable.v2.ResultSetMetadata other) {
      if (other == com.google.bigtable.v2.ResultSetMetadata.getDefaultInstance()) return this;
      switch (other.getSchemaCase()) {
        case PROTO_SCHEMA:
          {
            mergeProtoSchema(other.getProtoSchema());
            break;
          }
        case SCHEMA_NOT_SET:
          {
            break;
          }
      }
      this.mergeUnknownFields(other.getUnknownFields());
      onChanged();
      return this;
    }

    @java.lang.Override
    public final boolean isInitialized() {
      return true;
    }

    @java.lang.Override
    public Builder mergeFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      if (extensionRegistry == null) {
        throw new java.lang.NullPointerException();
      }
      try {
        boolean done = false;
        while (!done) {
          int tag = input.readTag();
          switch (tag) {
            case 0:
              done = true;
              break;
            case 10:
              {
                input.readMessage(getProtoSchemaFieldBuilder().getBuilder(), extensionRegistry);
                schemaCase_ = 1;
                break;
              } // case 10
            default:
              {
                if (!super.parseUnknownField(input, extensionRegistry, tag)) {
                  done = true; // was an endgroup tag
                }
                break;
              } // default:
          } // switch (tag)
        } // while (!done)
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        throw e.unwrapIOException();
      } finally {
        onChanged();
      } // finally
      return this;
    }

    private int schemaCase_ = 0;
    private java.lang.Object schema_;

    public SchemaCase getSchemaCase() {
      return SchemaCase.forNumber(schemaCase_);
    }

    public Builder clearSchema() {
      schemaCase_ = 0;
      schema_ = null;
      onChanged();
      return this;
    }

    private int bitField0_;

    private com.google.protobuf.SingleFieldBuilderV3<
            com.google.bigtable.v2.ProtoSchema,
            com.google.bigtable.v2.ProtoSchema.Builder,
            com.google.bigtable.v2.ProtoSchemaOrBuilder>
        protoSchemaBuilder_;
    /**
     *
     *
     * <pre>
     * Schema in proto format
     * </pre>
     *
     * <code>.google.bigtable.v2.ProtoSchema proto_schema = 1;</code>
     *
     * @return Whether the protoSchema field is set.
     */
    @java.lang.Override
    public boolean hasProtoSchema() {
      return schemaCase_ == 1;
    }
    /**
     *
     *
     * <pre>
     * Schema in proto format
     * </pre>
     *
     * <code>.google.bigtable.v2.ProtoSchema proto_schema = 1;</code>
     *
     * @return The protoSchema.
     */
    @java.lang.Override
    public com.google.bigtable.v2.ProtoSchema getProtoSchema() {
      if (protoSchemaBuilder_ == null) {
        if (schemaCase_ == 1) {
          return (com.google.bigtable.v2.ProtoSchema) schema_;
        }
        return com.google.bigtable.v2.ProtoSchema.getDefaultInstance();
      } else {
        if (schemaCase_ == 1) {
          return protoSchemaBuilder_.getMessage();
        }
        return com.google.bigtable.v2.ProtoSchema.getDefaultInstance();
      }
    }
    /**
     *
     *
     * <pre>
     * Schema in proto format
     * </pre>
     *
     * <code>.google.bigtable.v2.ProtoSchema proto_schema = 1;</code>
     */
    public Builder setProtoSchema(com.google.bigtable.v2.ProtoSchema value) {
      if (protoSchemaBuilder_ == null) {
        if (value == null) {
          throw new NullPointerException();
        }
        schema_ = value;
        onChanged();
      } else {
        protoSchemaBuilder_.setMessage(value);
      }
      schemaCase_ = 1;
      return this;
    }
    /**
     *
     *
     * <pre>
     * Schema in proto format
     * </pre>
     *
     * <code>.google.bigtable.v2.ProtoSchema proto_schema = 1;</code>
     */
    public Builder setProtoSchema(com.google.bigtable.v2.ProtoSchema.Builder builderForValue) {
      if (protoSchemaBuilder_ == null) {
        schema_ = builderForValue.build();
        onChanged();
      } else {
        protoSchemaBuilder_.setMessage(builderForValue.build());
      }
      schemaCase_ = 1;
      return this;
    }
    /**
     *
     *
     * <pre>
     * Schema in proto format
     * </pre>
     *
     * <code>.google.bigtable.v2.ProtoSchema proto_schema = 1;</code>
     */
    public Builder mergeProtoSchema(com.google.bigtable.v2.ProtoSchema value) {
      if (protoSchemaBuilder_ == null) {
        if (schemaCase_ == 1
            && schema_ != com.google.bigtable.v2.ProtoSchema.getDefaultInstance()) {
          schema_ =
              com.google.bigtable.v2.ProtoSchema.newBuilder(
                      (com.google.bigtable.v2.ProtoSchema) schema_)
                  .mergeFrom(value)
                  .buildPartial();
        } else {
          schema_ = value;
        }
        onChanged();
      } else {
        if (schemaCase_ == 1) {
          protoSchemaBuilder_.mergeFrom(value);
        } else {
          protoSchemaBuilder_.setMessage(value);
        }
      }
      schemaCase_ = 1;
      return this;
    }
    /**
     *
     *
     * <pre>
     * Schema in proto format
     * </pre>
     *
     * <code>.google.bigtable.v2.ProtoSchema proto_schema = 1;</code>
     */
    public Builder clearProtoSchema() {
      if (protoSchemaBuilder_ == null) {
        if (schemaCase_ == 1) {
          schemaCase_ = 0;
          schema_ = null;
          onChanged();
        }
      } else {
        if (schemaCase_ == 1) {
          schemaCase_ = 0;
          schema_ = null;
        }
        protoSchemaBuilder_.clear();
      }
      return this;
    }
    /**
     *
     *
     * <pre>
     * Schema in proto format
     * </pre>
     *
     * <code>.google.bigtable.v2.ProtoSchema proto_schema = 1;</code>
     */
    public com.google.bigtable.v2.ProtoSchema.Builder getProtoSchemaBuilder() {
      return getProtoSchemaFieldBuilder().getBuilder();
    }
    /**
     *
     *
     * <pre>
     * Schema in proto format
     * </pre>
     *
     * <code>.google.bigtable.v2.ProtoSchema proto_schema = 1;</code>
     */
    @java.lang.Override
    public com.google.bigtable.v2.ProtoSchemaOrBuilder getProtoSchemaOrBuilder() {
      if ((schemaCase_ == 1) && (protoSchemaBuilder_ != null)) {
        return protoSchemaBuilder_.getMessageOrBuilder();
      } else {
        if (schemaCase_ == 1) {
          return (com.google.bigtable.v2.ProtoSchema) schema_;
        }
        return com.google.bigtable.v2.ProtoSchema.getDefaultInstance();
      }
    }
    /**
     *
     *
     * <pre>
     * Schema in proto format
     * </pre>
     *
     * <code>.google.bigtable.v2.ProtoSchema proto_schema = 1;</code>
     */
    private com.google.protobuf.SingleFieldBuilderV3<
            com.google.bigtable.v2.ProtoSchema,
            com.google.bigtable.v2.ProtoSchema.Builder,
            com.google.bigtable.v2.ProtoSchemaOrBuilder>
        getProtoSchemaFieldBuilder() {
      if (protoSchemaBuilder_ == null) {
        if (!(schemaCase_ == 1)) {
          schema_ = com.google.bigtable.v2.ProtoSchema.getDefaultInstance();
        }
        protoSchemaBuilder_ =
            new com.google.protobuf.SingleFieldBuilderV3<
                com.google.bigtable.v2.ProtoSchema,
                com.google.bigtable.v2.ProtoSchema.Builder,
                com.google.bigtable.v2.ProtoSchemaOrBuilder>(
                (com.google.bigtable.v2.ProtoSchema) schema_, getParentForChildren(), isClean());
        schema_ = null;
      }
      schemaCase_ = 1;
      onChanged();
      return protoSchemaBuilder_;
    }

    @java.lang.Override
    public final Builder setUnknownFields(final com.google.protobuf.UnknownFieldSet unknownFields) {
      return super.setUnknownFields(unknownFields);
    }

    @java.lang.Override
    public final Builder mergeUnknownFields(
        final com.google.protobuf.UnknownFieldSet unknownFields) {
      return super.mergeUnknownFields(unknownFields);
    }

    // @@protoc_insertion_point(builder_scope:google.bigtable.v2.ResultSetMetadata)
  }

  // @@protoc_insertion_point(class_scope:google.bigtable.v2.ResultSetMetadata)
  private static final com.google.bigtable.v2.ResultSetMetadata DEFAULT_INSTANCE;

  static {
    DEFAULT_INSTANCE = new com.google.bigtable.v2.ResultSetMetadata();
  }

  public static com.google.bigtable.v2.ResultSetMetadata getDefaultInstance() {
    return DEFAULT_INSTANCE;
  }

  private static final com.google.protobuf.Parser<ResultSetMetadata> PARSER =
      new com.google.protobuf.AbstractParser<ResultSetMetadata>() {
        @java.lang.Override
        public ResultSetMetadata parsePartialFrom(
            com.google.protobuf.CodedInputStream input,
            com.google.protobuf.ExtensionRegistryLite extensionRegistry)
            throws com.google.protobuf.InvalidProtocolBufferException {
          Builder builder = newBuilder();
          try {
            builder.mergeFrom(input, extensionRegistry);
          } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            throw e.setUnfinishedMessage(builder.buildPartial());
          } catch (com.google.protobuf.UninitializedMessageException e) {
            throw e.asInvalidProtocolBufferException().setUnfinishedMessage(builder.buildPartial());
          } catch (java.io.IOException e) {
            throw new com.google.protobuf.InvalidProtocolBufferException(e)
                .setUnfinishedMessage(builder.buildPartial());
          }
          return builder.buildPartial();
        }
      };

  public static com.google.protobuf.Parser<ResultSetMetadata> parser() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.protobuf.Parser<ResultSetMetadata> getParserForType() {
    return PARSER;
  }

  @java.lang.Override
  public com.google.bigtable.v2.ResultSetMetadata getDefaultInstanceForType() {
    return DEFAULT_INSTANCE;
  }
}
