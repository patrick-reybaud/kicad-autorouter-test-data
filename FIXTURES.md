# Recordings of the Java reference

Every file of this repository, its size, the generator that writes it (in `generators/`, run against
the pinned Java engine and JVM from the working repository that produced it) and the KiCad tests
that read it (`qa/tests/pcbnew/autorouter/` in the KiCad tree, suite `qa_autorouter_core`). Files
ending in `.gz` are read through `fixture_stream.h`, which inflates the gzip twin of the name a test
asks for; regenerate a missing one with its generator and compress it with `gzip -9`.

123 files, 401.2 MB.

| File | MB | Generator | Read by |
| --- | ---: | --- | --- |
| `angle_java.tsv.gz` | 1.5 | `generators/generate_angle.py` | `test_freerouting_angle.cpp` |
| `autoroute_completion_java.tsv.gz` | 0.2 | `generators/generate_autoroute_completion.py` | `test_freerouting_autoroute_completion.cpp` |
| `autoroute_control_java.tsv.gz` | 0.1 | `generators/generate_autoroute_control.py` | `test_freerouting_autoroute_control.cpp` |
| `autoroute_engine_java.tsv.gz` | 0.2 | `generators/generate_autoroute_engine.py` | `test_freerouting_autoroute_engine.cpp` |
| `autoroute_item_java.tsv.gz` | 1.2 | `generators/generate_autoroute_item.py` | `test_freerouting_autoroute_item.cpp` |
| `batch_loop_java.json.gz` | 0.8 | `generators/generate_batch_loop.py` | — |
| `board_construction_java.tsv.gz` | 0.3 | `generators/generate_board_construction.py` | `test_freerouting_board_construction.cpp` |
| `board_construction_prefixes_java.tsv.gz` | 2.6 | `generators/generate_board_construction_prefixes.py` | `test_freerouting_board_construction_prefixes.cpp`, `test_freerouting_board_entry.cpp` |
| `board_copy_java.json.gz` | 1.5 | `generators/generate_board_copy.py` | — |
| `board_editing_java.tsv.gz` | 0.2 | `generators/generate_board_editing.py` | `test_freerouting_board_editing.cpp` |
| `board_entry_java.tsv.gz` | 2.3 | `generators/generate_board_entry.py` | `test_freerouting_board_entry.cpp` |
| `board_item_java.tsv.gz` | 0.3 | `generators/generate_board_item.py` | `test_freerouting_board_item.cpp` |
| `board_library_java.tsv.gz` | 0.6 | `generators/generate_board_library.py` | `test_freerouting_board_library.cpp` |
| `board_normalization_java.tsv.part0.gz` | 33.6 | `generators/generate_board_normalization.py` | `test_freerouting_board_normalization.cpp` |
| `board_normalization_java.tsv.part1.gz` | 34.6 | `generators/generate_board_normalization.py` | `test_freerouting_board_normalization.cpp` |
| `board_normalization_java.tsv.part2.gz` | 34.7 | `generators/generate_board_normalization.py` | `test_freerouting_board_normalization.cpp` |
| `board_normalization_java.tsv.part3.gz` | 34.7 | `generators/generate_board_normalization.py` | `test_freerouting_board_normalization.cpp` |
| `board_normalization_java.tsv.part4.gz` | 33.9 | `generators/generate_board_normalization.py` | `test_freerouting_board_normalization.cpp` |
| `board_queries_java.tsv` | 0.4 | `generators/generate_board_queries.py` | `test_freerouting_board_queries.cpp` |
| `board_rules_java.tsv.gz` | 0.5 | `generators/generate_board_rules.py` | `test_freerouting_board_rules.cpp` |
| `board_trace_java.tsv.gz` | 0.1 | `generators/generate_board_trace.py` | `test_freerouting_board_trace.cpp` |
| `board_unbound_entry_java.tsv` | 0.2 | board or fixture written by a test | `test_freerouting_board_entry.cpp` |
| `changed_area_java.json.gz` | 4.8 | `generators/generate_changed_area.py` | `test_freerouting_changed_area.cpp` |
| `circle_java.tsv.gz` | 0.5 | `generators/generate_circle.py` | `test_freerouting_circle.cpp` |
| `clearance_matrix_java.tsv.gz` | 0.2 | board or fixture written by a test | `test_freerouting_clearance_matrix.cpp` |
| `clearance_queries_java.tsv` | 0.5 | `generators/generate_clearance_queries.py` | `test_freerouting_clearance_queries.cpp` |
| `clearance_violations_java.tsv.gz` | 0.2 | `generators/generate_clearance_violations.py` | `test_freerouting_clearance_violations.cpp` |
| `collision_checks_java.json.gz` | 0.4 | `generators/generate_collision_checks.py` | `test_freerouting_collision_checks.cpp` |
| `complete_room_java.tsv.gz` | 0.4 | `generators/generate_complete_room.py` | `test_freerouting_complete_room.cpp` |
| `component_outline_java.tsv.gz` | 1.1 | `generators/generate_component_outline.py` | `test_freerouting_component_outline.cpp` |
| `component_rotation_java.tsv.gz` | 0.3 | `generators/generate_component_rotation.py` | `test_freerouting_components.cpp` |
| `components_java.tsv.gz` | 0.6 | `generators/generate_components.py` | `test_freerouting_components.cpp` |
| `connection_inserter_java.json.gz` | 5.8 | `generators/generate_connection_inserter.py` | `test_freerouting_connection_inserter.cpp` |
| `connection_items_java.tsv` | 0.7 | `generators/generate_connection_items.py` | `test_freerouting_connection_items.cpp` |
| `connection_java.tsv.gz` | 0.4 | `generators/generate_connection.py` | `test_freerouting_connection.cpp` |
| `connection_router_java.json.gz` | 33.4 | `generators/generate_connection_router.py` | — |
| `contact_queries_java.tsv` | 0.6 | `generators/generate_contact_queries.py` | `test_freerouting_contact_queries.cpp` |
| `destination_alias_java.tsv.gz` | 0.5 | `generators/generate_destination_alias.py` | `test_freerouting_destination_alias.cpp` |
| `destination_distance_java.tsv.gz` | 0.3 | `generators/generate_destination_distance.py` | `test_freerouting_destination_distance.cpp` |
| `drill_item_java.tsv.gz` | 0.3 | `generators/generate_drill_item.py` | `test_freerouting_drill_item.cpp` |
| `drill_move_java.tsv` | 0.3 | `generators/generate_drill_move.py` | `test_freerouting_drill_move.cpp` |
| `drill_page_java.tsv.gz` | 0.5 | `generators/generate_drill_page.py` | `test_freerouting_drill_page.cpp` |
| `exact_coordinates_java.tsv.gz` | 9.4 | `generators/generate_exact_coordinates.py` | `test_freerouting_exact_coordinates.cpp` |
| `exact_line_java.tsv.gz` | 1.5 | `generators/generate_exact_line.py` | `test_freerouting_exact_line.cpp` |
| `expansion_door_java.tsv.gz` | 1.1 | `generators/generate_expansion_door.py` | `test_freerouting_expansion_door.cpp` |
| `expansion_drill_java.tsv.gz` | 0.6 | `generators/generate_expansion_drill.py` | `test_freerouting_expansion_drill.cpp` |
| `fanout_java.json.gz` | 6.0 | `generators/generate_fanout.py` | — |
| `forced_trace_java.json.gz` | 10.5 | `generators/generate_forced_trace.py` | `test_freerouting_forced_trace.cpp` |
| `forced_via_java.json.gz` | 19.7 | `generators/generate_forced_via.py` | — |
| `integer_group_map_java.tsv.gz` | 0.5 | board or fixture written by a test | `test_freerouting_board_normalization.cpp` |
| `item_contacts_java.tsv` | 0.3 | `generators/generate_item_contacts.py` | `test_freerouting_item_contacts.cpp` |
| `item_route_result_java.tsv` | 0.2 | `generators/generate_item_route_result.py` | `test_freerouting_item_route_result.cpp` |
| `item_state_java.tsv.gz` | 0.2 | `generators/generate_item_state.py` | `test_freerouting_item_state.cpp` |
| `item_tree_cache_java.tsv.gz` | 0.1 | `generators/generate_item_tree_cache.py` | `test_freerouting_item_tree_cache.cpp` |
| `line_sort_java.tsv.gz` | 7.3 | `generators/generate_line_sort.py` | `test_freerouting_line_sort.cpp` |
| `locator_java.json` | 0.9 | `generators/generate_locator.py` | `test_freerouting_locator.cpp` |
| `maze_door_java.tsv.gz` | 0.3 | `generators/generate_maze_door.py` | `test_freerouting_maze_door.cpp` |
| `maze_expansion_java.json.gz` | 4.8 | `generators/generate_maze_expansion.py` | `test_freerouting_maze_expansion.cpp` |
| `maze_geometry_java.tsv.gz` | 0.2 | `generators/generate_maze_geometry.py` | `test_freerouting_maze_geometry.cpp` |
| `maze_initialization_java.tsv.gz` | 0.2 | `generators/generate_maze_initialization.py` | `test_freerouting_maze_initialization.cpp` |
| `maze_list_java.tsv.gz` | 0.5 | `generators/generate_maze_list.py` | `test_freerouting_maze_list.cpp` |
| `maze_queue_java.tsv.gz` | 0.3 | `generators/generate_maze_queue.py` | `test_freerouting_maze_queue.cpp` |
| `maze_ripup_java.json.gz` | 0.9 | `generators/generate_maze_ripup.py` | `test_freerouting_maze_ripup.cpp` |
| `maze_search_java.json.gz` | 13.5 | `generators/generate_maze_search.py` | `test_freerouting_maze_search.cpp` |
| `maze_section_java.tsv.gz` | 0.1 | `generators/generate_maze_section.py` | `test_freerouting_maze_section.cpp` |
| `maze_shove_java.json.gz` | 2.1 | `generators/generate_maze_shove.py` | `test_freerouting_maze_shove.cpp` |
| `model_rotation_java.tsv.gz` | 0.1 | `generators/generate_model_rotation.py` | `test_freerouting_model_rotation.cpp` |
| `neighbour_order_java.tsv.gz` | 0.6 | `generators/generate_neighbour_order.py` | `test_freerouting_neighbour_order.cpp` |
| `nets_java.tsv` | 0.4 | `generators/generate_nets.py` | `test_freerouting_nets.cpp` |
| `neutral_items_java.tsv.gz` | 2.8 | board or fixture written by a test | `test_freerouting_neutral_items.cpp` |
| `neutral_model_java.tsv.gz` | 0.0 | board or fixture written by a test | `test_freerouting_neutral_model.cpp` |
| `obstacle_area_java.tsv.part0.gz` | 1.4 | `generators/generate_obstacle_area.py` | `test_freerouting_obstacle_area.cpp` |
| `obstacle_area_java.tsv.part1.gz` | 1.5 | `generators/generate_obstacle_area.py` | `test_freerouting_obstacle_area.cpp` |
| `obstacle_area_java.tsv.part2.gz` | 1.5 | `generators/generate_obstacle_area.py` | `test_freerouting_obstacle_area.cpp` |
| `obstacle_area_java.tsv.part3` | 5.0 | `generators/generate_obstacle_area.py` | `test_freerouting_obstacle_area.cpp` |
| `octagonal_neighbours_java.tsv.gz` | 1.2 | `generators/generate_octagonal_neighbours.py` | `test_freerouting_octagonal_neighbours.cpp` |
| `octagonal_room_java.tsv.gz` | 2.1 | `generators/generate_octagonal_room.py` | `test_freerouting_octagonal_room.cpp` |
| `optimizer_mt_java.json.gz` | 0.1 | `generators/generate_optimizer_mt.py` | — |
| `ordered_set_mutation_java.tsv.gz` | 1.0 | `generators/generate_ordered_set_mutation.py` | `test_freerouting_ordered_set_mutation.cpp` |
| `orthogonal_neighbours_java.tsv.gz` | 0.5 | `generators/generate_orthogonal_neighbours.py` | `test_freerouting_orthogonal_neighbours.cpp` |
| `orthogonal_room_java.tsv.gz` | 0.7 | `generators/generate_orthogonal_room.py` | `test_freerouting_orthogonal_room.cpp` |
| `padstack_java.tsv.gz` | 0.2 | `generators/generate_padstack.py` | `test_freerouting_padstack.cpp` |
| `pass_runner_java.json.gz` | 0.7 | `generators/generate_pass_runner.py` | — |
| `pin_model_java.tsv.gz` | 0.3 | `generators/generate_pin_model.py` | `test_freerouting_pin.cpp` |
| `planar_java.tsv.gz` | 1.0 | `generators/generate_planar.py` | `test_freerouting_planar.cpp` |
| `point_identity_java.json.gz` | 0.2 | `generators/generate_point_identity.py` | `test_freerouting_point_identity.cpp` |
| `polygon_shape_java.tsv.gz` | 0.6 | `generators/generate_polygon_shape.py` | `test_freerouting_polygon_shape.cpp` |
| `polyline_area_java.tsv.gz` | 0.1 | `generators/generate_polyline_area.py` | `test_freerouting_polyline_area.cpp` |
| `polyline_java.tsv.gz` | 1.4 | `generators/generate_polyline.py` | `test_freerouting_polyline.cpp` |
| `polyline_tightening_java.json.gz` | 7.5 | `generators/generate_polyline_tightening.py` | `test_freerouting_polyline_tightening.cpp` |
| `public_route_java.json.gz` | 9.8 | `generators/generate_public_route.py` | — |
| `regular_shapes_java.tsv.gz` | 2.9 | `generators/generate_regular_shapes.py` | `test_freerouting_regular_shapes.cpp` |
| `room_completion_java.tsv.gz` | 1.0 | `generators/generate_room_completion.py` | `test_freerouting_room_completion.cpp` |
| `room_discovery_java.tsv.gz` | 0.3 | `generators/generate_room_discovery.py` | `test_freerouting_room_discovery.cpp` |
| `room_dispatch_java.tsv.gz` | 0.2 | `generators/generate_room_dispatch.py` | `test_freerouting_room_dispatch.cpp` |
| `rotation_java.tsv.gz` | 5.7 | board or fixture written by a test | `test_freerouting_rotation.cpp` |
| `rotation_model_java.tsv` | 0.8 | `generators/generate_rotation_model.py` | `test_freerouting_rotation.cpp` |
| `routing_rules_java.tsv.gz` | 0.7 | `generators/generate_routing_rules.py` | `test_freerouting_routing_rules.cpp` |
| `routing_search_java.json.gz` | 0.2 | `generators/generate_routing_search.py` | `test_freerouting_routing_search.cpp` |
| `search_queries_java.tsv` | 0.4 | `generators/generate_search_queries.py` | `test_freerouting_search_queries.cpp` |
| `search_tree_manager_java.tsv.gz` | 0.2 | `generators/generate_search_tree_manager.py` | `test_freerouting_search_tree_manager.cpp` |
| `search_updates_java.tsv.gz` | 0.1 | `generators/generate_search_updates.py` | `test_freerouting_search_updates.cpp` |
| `shape_entry_java.json.gz` | 1.3 | `generators/generate_shape_entry.py` | `test_freerouting_shape_entry.cpp` |
| `shape_trace_entries_java.json.gz` | 2.2 | `generators/generate_shape_trace_entries.py` | `test_freerouting_shape_trace_entries.cpp` |
| `shape_tree_java.tsv.gz` | 1.2 | `generators/generate_shape_tree.py` | `test_freerouting_shape_tree.cpp` |
| `shove_checks_java.json.gz` | 1.4 | `generators/generate_shove_checks.py` | `test_freerouting_shove_checks.cpp` |
| `shove_insertion_java.json.gz` | 4.3 | `generators/generate_shove_insertion.py` | `test_freerouting_shove_insertion.cpp` |
| `simplex_java.tsv.gz` | 1.2 | `generators/generate_simplex.py` | `test_freerouting_simplex.cpp` |
| `special_areas_java.tsv.gz` | 1.1 | `generators/generate_special_areas.py` | `test_freerouting_special_areas.cpp` |
| `spring_over_java.json.gz` | 1.8 | `generators/generate_spring_over.py` | `test_freerouting_spring_over.cpp` |
| `tie_pin_java.tsv.gz` | 0.1 | `generators/generate_tie_pin.py` | `test_freerouting_tie_pin.cpp` |
| `tightener45_java.json.gz` | 1.4 | `generators/generate_tightener45.py` | `test_freerouting_tightener45.cpp` |
| `tightener90_java.json.gz` | 0.7 | `generators/generate_tightener90.py` | `test_freerouting_tightener90.cpp` |
| `tile_shape_java.tsv.gz` | 1.1 | `generators/generate_tile_shape.py` | `test_freerouting_tile_shape.cpp` |
| `timespan_java.tsv` | 0.4 | `generators/generate_timespan.py` | `test_freerouting_timespan.cpp` |
| `trace_policy_java.tsv` | 0.8 | `generators/generate_trace_policy.py` | `test_freerouting_trace_policy.cpp` |
| `trace_updates_java.tsv.gz` | 0.2 | `generators/generate_trace_updates.py` | `test_freerouting_trace_updates.cpp` |
| `trig_fdlibm_java.tsv` | 1.0 | board or fixture written by a test | `test_freerouting_java25_math.cpp`, `test_freerouting_trig.cpp` |
| `undoable_objects_java.tsv.gz` | 0.3 | `generators/generate_undoable_objects.py` | `test_freerouting_undoable_objects.cpp` |
| `via_insertion_java.tsv.gz` | 0.6 | `generators/generate_via_insertion.py` | `test_freerouting_via_insertion.cpp` |
| `via_model_java.tsv.gz` | 0.3 | `generators/generate_via_model.py` | `test_freerouting_via_model.cpp` |
| `via_movement_java.json.gz` | 4.3 | `generators/generate_via_movement.py` | `test_freerouting_via_movement.cpp` |
| `via_optimizer_java.json.gz` | 4.8 | `generators/generate_via_optimizer.py` | — |
