package com.enron.email.parsing

import java.io.{Closeable, OutputStream}

import org.apache.avro.file.{CodecFactory, DataFileWriter}
import org.apache.avro.specific.SpecificDatumWriter
import org.apache.commons.io.IOUtils

/**
 * Used to write MailRecord objects to an Avro
 * collection file. Call open to initialize,
 * then append mail records via append method.
 * Must call close to properly close the Avro
 * collection file.
 */
// scalastyle:off
class MailRecordAvroWriter extends Closeable {

  private var mailRecordWriter: DataFileWriter[MailRecord] = _

  def open(out: OutputStream): Unit = {
    val datumWriter = new SpecificDatumWriter[MailRecord](classOf[MailRecord])
    mailRecordWriter = new DataFileWriter[MailRecord](datumWriter)
    mailRecordWriter.setCodec(CodecFactory.snappyCodec())
    mailRecordWriter.create(MailRecord.getClassSchema(), out)
  }

  def append(record: MailRecord): Unit = {
    mailRecordWriter.append(record)
  }

  override def close() {
    IOUtils.closeQuietly(mailRecordWriter)
    mailRecordWriter = null
  }

}
