package com.geeksville.gdatasource

import com.google.visualization.datasource.DataSourceServlet
import com.google.visualization.datasource.query.Query
import javax.servlet.http.HttpServletRequest
import com.google.visualization.datasource.datatable.DataTable
import java.util.ArrayList
import com.google.visualization.datasource.datatable.ColumnDescription
import com.google.visualization.datasource.datatable.value.ValueType
import com.google.visualization.datasource.base.TypeMismatchException

// This example extends DataSourceServlet

class GDatasourceServlet extends DataSourceServlet {

  override def isRestrictedAccessMode() = false

  override def generateDataTable(query: Query, request: HttpServletRequest): DataTable = {
    // Create a data table,
    val data = new DataTable();
    val cd = new ArrayList[ColumnDescription]();
    cd.add(new ColumnDescription("name", ValueType.TEXT, "Animal name"));
    cd.add(new ColumnDescription("link", ValueType.TEXT, "Link to wikipedia"));
    cd.add(new ColumnDescription("population", ValueType.NUMBER, "Population size"));
    cd.add(new ColumnDescription("vegeterian", ValueType.BOOLEAN, "Vegetarian?"));

    data.addColumns(cd);

    // Fill the data table.
    data.addRowFromValues("Aye-aye", "http://en.wikipedia.org/wiki/Aye-aye", 100: Integer, true: java.lang.Boolean);
    data.addRowFromValues("Sloth", "http://en.wikipedia.org/wiki/Sloth", 300: Integer, true: java.lang.Boolean);
    data.addRowFromValues("Leopard", "http://en.wikipedia.org/wiki/Leopard", 50: Integer, false: java.lang.Boolean);
    data.addRowFromValues("Tiger", "http://en.wikipedia.org/wiki/Tiger", 80: Integer, false: java.lang.Boolean);

    data
  }
}
