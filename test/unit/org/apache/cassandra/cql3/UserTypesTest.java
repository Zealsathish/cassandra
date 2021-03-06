/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.cassandra.cql3;

import org.junit.Test;

public class UserTypesTest extends CQLTester
{
    @Test
    public void testInvalidField() throws Throwable
    {
        String myType = createType("CREATE TYPE %s (f int)");
        createTable("CREATE TABLE %s (k int PRIMARY KEY, v frozen<" + myType + ">)");

        // 's' is not a field of myType
        assertInvalid("INSERT INTO %s (k, v) VALUES (?, {s : ?})", 0, 1);
    }

    @Test
    public void testCassandra8105() throws Throwable
    {
        String ut1 = createType("CREATE TYPE %s (a int, b int)");
        String ut2 = createType("CREATE TYPE %s (j frozen<" + KEYSPACE + "." + ut1 + ">, k int)");
        createTable("CREATE TABLE %s (x int PRIMARY KEY, y set<frozen<" + KEYSPACE + "." + ut2 + ">>)");
        execute("INSERT INTO %s (x, y) VALUES (1, { { k: 1 } })");

        String ut3 = createType("CREATE TYPE %s (a int, b int)");
        String ut4 = createType("CREATE TYPE %s (j frozen<" + KEYSPACE + "." + ut3 + ">, k int)");
        createTable("CREATE TABLE %s (x int PRIMARY KEY, y list<frozen<" + KEYSPACE + "." + ut4 + ">>)");
        execute("INSERT INTO %s (x, y) VALUES (1, [ { k: 1 } ])");

        String ut5 = createType("CREATE TYPE %s (a int, b int)");
        String ut6 = createType("CREATE TYPE %s (i int, j frozen<" + KEYSPACE + "." + ut5 + ">)");
        createTable("CREATE TABLE %s (x int PRIMARY KEY, y set<frozen<" + KEYSPACE + "." + ut6 + ">>)");
        execute("INSERT INTO %s (x, y) VALUES (1, { { i: 1 } })");
    }

    @Test
    public void testFor7684() throws Throwable
    {
        String myType = createType("CREATE TYPE %s (x double)");
        createTable("CREATE TABLE %s (k int, v frozen<" + myType + ">, b boolean static, PRIMARY KEY (k, v))");

        execute("INSERT INTO %s(k, v) VALUES (?, {x:?})", 1, -104.99251);
        execute("UPDATE %s SET b = ? WHERE k = ?", true, 1);

        assertRows(execute("SELECT v.x FROM %s WHERE k = ? AND v = {x:?}", 1, -104.99251),
            row(-104.99251)
        );

        flush();

        assertRows(execute("SELECT v.x FROM %s WHERE k = ? AND v = {x:?}", 1, -104.99251),
            row(-104.99251)
        );
    }

    @Test
    public void testCreateInvalidTablesWithUDT() throws Throwable
    {
        String myType = createType("CREATE TYPE %s (f int)");

        // Using a UDT without frozen shouldn't work
        assertInvalidMessage("Non-frozen User-Defined types are not supported, please use frozen<>",
                             "CREATE TABLE " + KEYSPACE + ".wrong (k int PRIMARY KEY, v " + KEYSPACE + '.' + myType + ")");

        assertInvalidMessage("Statement on keyspace " + KEYSPACE + " cannot refer to a user type in keyspace otherkeyspace;" +
                             " user types can only be used in the keyspace they are defined in",
                             "CREATE TABLE " + KEYSPACE + ".wrong (k int PRIMARY KEY, v frozen<otherKeyspace.myType>)");

        assertInvalidMessage("Unknown type " + KEYSPACE + ".unknowntype",
                             "CREATE TABLE " + KEYSPACE + ".wrong (k int PRIMARY KEY, v frozen<" + KEYSPACE + '.' + "unknownType>)");
    }

    @Test
    public void testAlterUDT() throws Throwable
    {
        String myType = KEYSPACE + '.' + createType("CREATE TYPE %s (a int)");
        createTable("CREATE TABLE %s (a int PRIMARY KEY, b frozen<" + myType + ">)");
        execute("INSERT INTO %s (a, b) VALUES (1, {a: 1})");

        assertRows(execute("SELECT b.a FROM %s"), row(1));

        flush();

        execute("ALTER TYPE " + myType + " ADD b int");
        execute("INSERT INTO %s (a, b) VALUES (2, {a: 2, b :2})");

        assertRows(execute("SELECT b.a, b.b FROM %s"),
                   row(1, null),
                   row(2, 2));

        flush();

        assertRows(execute("SELECT b.a, b.b FROM %s"),
                   row(1, null),
                   row(2, 2));
    }

    @Test
    public void testUDTWithUnsetValues() throws Throwable
    {
        // set up
        String myType = createType("CREATE TYPE %s (x int, y int)");
        String myOtherType = createType("CREATE TYPE %s (a frozen<" + myType + ">)");
        createTable("CREATE TABLE %s (k int PRIMARY KEY, v frozen<" + myType + ">, z frozen<" + myOtherType + ">)");

        assertInvalidMessage("Invalid unset value for field 'y' of user defined type " + myType,
                "INSERT INTO %s (k, v) VALUES (10, {x:?, y:?})", 1, unset());

        assertInvalidMessage("Invalid unset value for field 'y' of user defined type " + myType,
                "INSERT INTO %s (k, v, z) VALUES (10, {x:?, y:?}, {a:{x: ?, y: ?}})", 1, 1, 1, unset());
    }
}
