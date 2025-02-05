/***********************************************************************
 * Copyright (c) 2013-2022 Commonwealth Computer Research, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, Version 2.0
 * which accompanies this distribution and is available at
 * http://www.opensource.org/licenses/apache2.0.php.
 ***********************************************************************/

package org.locationtech.geomesa.gt.partition.postgis.dialect
package tables

import java.util.Locale

/**
 * Write ahead log, partitioned using inheritance to avoid any restraints on data overlap between partitions.
 * All writes get directed to the main partition, which is identified with the suffix `_writes`. Every 10 minutes,
 * the current writes partition is renamed based on a sequence number, and a new writes partition is created
 */
object WriteAheadTable extends SqlStatements {

  def writesPartition(info: TypeInfo): TableIdentifier =
    TableIdentifier(info.schema.raw, info.tables.writeAhead.name.raw + "_writes")

  override protected def createStatements(info: TypeInfo): Seq[String] = {
    val table = info.tables.writeAhead
    val partition = writesPartition(info).qualified
    val seq = s"CREATE SEQUENCE IF NOT EXISTS ${escape(table.name.raw, "seq")} AS smallint MINVALUE 1 MAXVALUE 999 CYCLE;"
    // rename the table created by the JdbcDataStore to be the write ahead table
    val rename =
      s"""DO $$$$
         |BEGIN
         |  IF NOT EXISTS (SELECT FROM pg_tables WHERE schemaname = ${info.schema.asLiteral} AND tablename = ${table.name.asLiteral}) THEN
         |    ALTER TABLE ${info.tables.view.name.qualified} RENAME TO ${table.name.quoted};
         |  ELSE
         |    DROP TABLE ${info.tables.view.name.qualified};
         |  END IF;
         |END$$$$;""".stripMargin
    // drop the index created by the JDBC data store on the parent table, as all the data is in the inherited table
    val dropIndices = info.cols.geoms.map { col =>
      s"DROP INDEX IF EXISTS ${escape("spatial", info.tables.view.name.raw, col.raw.toLowerCase(Locale.US))};"
    }
    val move = table.tablespace.toSeq.map { ts =>
      s"ALTER TABLE ${table.name.qualified} SET TABLESPACE ${ts.quoted};"
    }
    val (tableTs, indexTs) = table.tablespace match {
      case None => ("", "")
      case Some(ts) => (s" TABLESPACE ${ts.quoted}", s" USING INDEX TABLESPACE ${ts.quoted}")
    }
    val child =
      s"""CREATE TABLE IF NOT EXISTS $partition (
         |  CONSTRAINT ${escape(table.name.raw, "000_pkey")} PRIMARY KEY (fid, ${info.cols.dtg.quoted})$indexTs
         |) INHERITS (${table.name.qualified})${table.storage.opts}$tableTs;""".stripMargin
    val dtgIndex =
      s"""CREATE INDEX IF NOT EXISTS ${escape(table.name.raw, info.cols.dtg.raw, "000")}
         |  ON $partition (${info.cols.dtg.quoted})$tableTs;""".stripMargin
    val geomIndices = info.cols.geoms.map { col =>
      s"""CREATE INDEX IF NOT EXISTS ${escape("spatial", table.name.raw, col.raw.toLowerCase(Locale.US), "000")}
         |  ON $partition USING gist(${col.quoted})$tableTs;""".stripMargin
    }
    val indices = info.cols.indexed.map { col =>
      s"""CREATE INDEX IF NOT EXISTS ${escape(table.name.raw, col.raw, "000")}
         |  ON $partition (${col.quoted})$tableTs;""".stripMargin
    }
    Seq(seq, rename) ++ dropIndices ++ move ++ Seq(child, dtgIndex) ++ geomIndices ++ indices
  }

  override protected def dropStatements(info: TypeInfo): Seq[String] = {
    Seq(
      s"DROP TABLE IF EXISTS ${info.tables.writeAhead.name.qualified};", // note: actually gets dropped by jdbc store
      s"DROP SEQUENCE IF EXISTS ${escape(info.tables.writeAhead.name.raw, "seq")};"
    )
  }
}
