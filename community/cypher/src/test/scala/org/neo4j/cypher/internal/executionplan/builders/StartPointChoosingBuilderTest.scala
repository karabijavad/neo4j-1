/**
 * Copyright (c) 2002-2013 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.executionplan.builders

import org.junit.Test
import org.neo4j.cypher.internal.executionplan.PartiallySolvedQuery
import org.neo4j.cypher.internal.commands._
import expressions.{Property, Literal, Identifier}
import values.LabelName
import org.neo4j.cypher.internal.commands.HasLabel
import org.neo4j.cypher.internal.spi.PlanContext
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import org.neo4j.cypher.internal.pipes.FakePipe
import org.neo4j.cypher.internal.symbols.NodeType
import org.neo4j.graphdb.Direction


class StartPointChoosingBuilderTest extends BuilderTest with MockitoSugar {
  def builder = new StartPointChoosingBuilder

  override val context = mock[PlanContext]

  @Test
  def should_create_multiple_start_points_for_disjoint_graphs() {
    // Given
    val identifier = "n"
    val otherIdentifier = "p"

    val query = q(
      patterns = Seq(SingleNode(identifier), SingleNode(otherIdentifier))
    )

    // When
    val plan = assertAccepts(query)

    // Then
    assert(plan.query.start.toList === Seq(Unsolved(AllNodes(identifier)),
                                           Unsolved(AllNodes(otherIdentifier))))
  }

  @Test
  def should_not_create_start_points_tail_query() {
    // Given
    val identifier = "n"
    val otherIdentifier = "p"

    val query = q(
      patterns = Seq(SingleNode(identifier)),
      tail = Some(q(
        patterns = Seq(SingleNode(otherIdentifier))
      ))
    )

    // When
    val plan = assertAccepts(query)

    // Then
    assert(plan.query.start.toList          === Seq(Unsolved(AllNodes(identifier))))
    assert(plan.query.tail.get.start.toList === Seq())
  }

  @Test
  def should_create_multiple_index_start_points_when_available_for_disjoint_graphs() {
    // Given
    val identifier = "n"
    val otherIdentifier = "p"

    val query = q(
      patterns = Seq(SingleNode(identifier), SingleNode(otherIdentifier)),
      where = Seq(HasLabel(Identifier(identifier), Seq(LabelName("Person"))),
                  Equals(Property(Identifier(identifier),"prop1"), Literal("banana")))
    )

    when( context.getIndexRuleId( "Person", "prop1" ) ).thenReturn( Some(1337l) )

    // When
    val plan = assertAccepts(query)

    // Then
    assert(plan.query.start.toList === Seq(
      Unsolved(SchemaIndex(identifier, "Person", "prop1", None)),
      Unsolved(AllNodes(otherIdentifier))))
  }

  @Test
  def should_not_accept_queries_with_start_items_on_all_levels() {
    assertRejects(
      q(start = Seq(NodeById("n", 0)))
    )

    assertRejects(
      q(start = Seq(NodeById("n", 0)))
    )
  }

  @Test
  def should_pick_an_index_if_only_one_possible_exists() {
    // Given
    val identifier = "n"
    val label = "Person"
    val property = "prop"
    val expression = Literal(42)
    val query = q(where = Seq(
      HasLabel(Identifier(identifier), Seq(LabelName(label))),
      Equals(Property(Identifier(identifier), property), expression)
    ), patterns = Seq(
      SingleNode(identifier)
    ))

    when( context.getIndexRuleId( "Person", "prop" ) ).thenReturn( Some(1337l) )

    // When
    val plan = assertAccepts(query)

    // Then
    assert(plan.query.start.toList === Seq(Unsolved(SchemaIndex(identifier, label, property, None))))
  }

  @Test
  def should_pick_an_index_if_only_one_possible_exists_other_side() {
    // Given
    val identifier = "n"
    val label = "Person"
    val property = "prop"
    val expression = Literal(42)
    val query = q(where = Seq(
      HasLabel(Identifier(identifier), Seq(LabelName(label))),
      Equals(expression, Property(Identifier(identifier), property))
    ), patterns = Seq(
      SingleNode(identifier)
    ))

    when( context.getIndexRuleId( "Person", "prop" ) ).thenReturn( Some(1337l) )

    // When
    val plan = assertAccepts(query)

    // Then
    assert(plan.query.start.toList === List(Unsolved(SchemaIndex(identifier, label, property, None))))
  }

  @Test
  def should_pick_any_index_available() {
    // Given
    val identifier = "n"
    val label = "Person"
    val property1 = "prop1"
    val property2 = "prop2"
    val expression = Literal(42)
    val query = q(where = Seq(
      HasLabel(Identifier(identifier), Seq(LabelName(label))),
      Equals(Property(Identifier(identifier), property1), expression),
      Equals(Property(Identifier(identifier), property2), expression)
    ), patterns = Seq(
      SingleNode(identifier)
    ))

    when( context.getIndexRuleId( "Person", "prop1" ) ).thenReturn( Some(1337l) )
    when( context.getIndexRuleId( "Person", "prop2" ) ).thenReturn( Some(1338l) )

    // When
    val result = assertAccepts(query).query

    // Then
    assert(result.start.exists(_.token.isInstanceOf[SchemaIndex]))
  }

  @Test
  def should_produce_label_start_points_when_no_property_predicate_is_used() {
    // Given MATCH n:Person
    val identifier = "n"
    val label = "Person"
    val query = q(where = Seq(
      HasLabel(Identifier(identifier), Seq(LabelName(label)))
    ), patterns = Seq(
      SingleNode(identifier)
    ))

    // When
    val plan = assertAccepts(query)

    assert(plan.query.start.toList === List(Unsolved(NodeByLabel("n", "Person"))))
  }

  @Test
  def should_produce_label_start_points_when_no_matching_index_exist() {
    // Given
    val identifier = "n"
    val label = "Person"
    val property = "prop"
    val expression = Literal(42)
    val query = q(where = Seq(
      HasLabel(Identifier(identifier), Seq(LabelName(label))),
      Equals(Property(Identifier(identifier), property), expression)
    ), patterns = Seq(
      SingleNode(identifier)
    ))

    when( context.getIndexRuleId( "Person", "prop" ) ).thenReturn( None )

    // When
    val plan = assertAccepts(query)

    // Then
    assert(plan.query.start.toList === Seq(Unsolved(NodeByLabel("n", "Person"))))
  }

  @Test
  def should_pick_a_global_start_point_if_nothing_else_is_possible() {
    // Given
    val identifier = "n"

    val query = PartiallySolvedQuery().copy(
      patterns = Seq(Unsolved(SingleNode(identifier)))
    )
    // When
    val plan = assertAccepts(query)

    // Then
    assert(plan.query.start.toList === Seq(Unsolved(AllNodes(identifier))))
  }

  @Test
  def should_be_able_to_figure_out_shortest_path_patterns() {
    // Given
    val node1 = "a"
    val node2 = "b"
    val label = "Person"
    val property = "prop"
    val expression1 = Literal(42)
    val expression2 = Literal(666)

    // MATCH p=shortestPath( (a:Person{prop:42}) -[*]-> (b{prop:666}) )
    val query = q(
      where = Seq(
        HasLabel(Identifier(node1), Seq(LabelName(label))),
        Equals(Property(Identifier(node1), property), expression1),
        Equals(Property(Identifier(node2), property), expression2)),

      patterns = Seq(
        ShortestPath("p", node1, node2, Nil, Direction.OUTGOING, None, optional = false, single = true, None))
    )

    when(context.getIndexRuleId(label, property)).thenReturn(None)

    // When
    val plan = assertAccepts(query)

    // Then
    assert(plan.query.start.toList === Seq(
      Unsolved(NodeByLabel(node1, label)),
      Unsolved(AllNodes(node2))))
  }

  @Test
  def should_not_introduce_start_points_if_provided_by_last_pipe() {
    // Given
    val identifier = "n"
    val otherIdentifier = "x"
    val pipe = new FakePipe(Iterator.empty, identifier -> NodeType())
    val query = q(
      patterns = Seq(SingleNode(identifier), SingleNode(otherIdentifier))
    )

    // When
    val plan = assertAccepts(pipe, query)

    // Then
    assert(plan.query.start.toList === Seq(Unsolved(AllNodes(otherIdentifier))))
  }

  private def q(start: Seq[StartItem] = Seq(),
                where: Seq[Predicate] = Seq(),
                patterns: Seq[Pattern] = Seq(),
                returns: Seq[ReturnColumn] = Seq(),
                tail:Option[PartiallySolvedQuery] = None) =
    PartiallySolvedQuery().copy(
      start = start.map(Unsolved(_)),
      where = where.map(Unsolved(_)),
      patterns = patterns.map(Unsolved(_)),
      returns = returns.map(Unsolved(_)),
      tail = tail
    )
}