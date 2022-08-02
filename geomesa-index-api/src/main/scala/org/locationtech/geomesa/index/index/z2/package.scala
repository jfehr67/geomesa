/***********************************************************************
 * Copyright (c) 2013-2022 Commonwealth Computer Research, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, Version 2.0
 * which accompanies this distribution and is available at
 * http://www.opensource.org/licenses/apache2.0.php.
 ***********************************************************************/

package org.locationtech.geomesa.index.index

import org.locationtech.geomesa.curve.{XZ2SFC, Z2SFC}
import org.locationtech.geomesa.filter.FilterValues
import org.locationtech.jts.geom.Geometry

package object z2 {

  case class Z2IndexValues(sfc: Z2SFC,
                           geometries: FilterValues[Geometry],
                           @deprecated("Use spatialBounds instead.")
                           bounds: Seq[(Double, Double, Double, Double)]) extends SpatialIndexValues {
    override def spatialBounds: Seq[(Double, Double, Double, Double)] = bounds
  }

  case class XZ2IndexValues(sfc: XZ2SFC,
                            geometries: FilterValues[Geometry],
                            @deprecated("Use spatialBounds instead.")
                            bounds: Seq[(Double, Double, Double, Double)]) extends SpatialIndexValues {
    override def spatialBounds: Seq[(Double, Double, Double, Double)] = bounds
  }
}
