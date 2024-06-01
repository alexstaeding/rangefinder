"use client";

import React, {useEffect, useRef} from 'react';
import * as d3 from 'd3';
import {SimulationNodeDatum} from "d3-force";
import {Data, Link, Node} from './data';

const ForceGraph: React.FC<Data> = ({nodes, links}) => {
  const svgRef = useRef<SVGSVGElement>(null);
  const width = 928;
  const height = 680;
  useEffect(() => {

    const color = d3.scaleOrdinal(d3.schemeCategory10);

    const svg = d3.select(svgRef.current)
      .attr('viewBox', [-width / 2, -height / 2, width, height])
      .style('max-width', '100%')
      .style('height', 'auto');

    svg.selectAll('*').remove();

    const simulation = d3.forceSimulation<Node, Link>(nodes)
      .force('link', d3.forceLink<Node, Link>(links).id(d => d.id).distance(100))
      .force('charge', d3.forceManyBody().strength(-300))
      .force('x', d3.forceX())
      .force('y', d3.forceY());

    const link = svg.append('g')
      .attr('stroke', '#999')
      .attr('stroke-opacity', 0.6)
      .selectAll('line')
      .data(links)
      .join('line')
      .attr('stroke-width', d => Math.sqrt(d.value));

    const node = svg.append('g')
      .attr('stroke', '#fff')
      .attr('stroke-width', 1.5)
      .selectAll('circle')
      .data(nodes)
      .join('circle')
      .attr('r', 10)
      .attr('fill', d => color(d.group));

    node.append('title')
      .text(d => d.id);

    // @ts-ignore
    node.call(d3.drag<Element, Node>()
      .on('start', dragstarted)
      .on('drag', dragged)
      .on('end', dragended))

    simulation.on('tick', () => {
      link
        .attr('x1', d => (d.source as SimulationNodeDatum).x || 0)
        .attr('y1', d => (d.source as SimulationNodeDatum).y || 0)
        .attr('x2', d => (d.target as SimulationNodeDatum).x || 0)
        .attr('y2', d => (d.target as SimulationNodeDatum).y || 0);

      node
        .attr('cx', d => d.x || 0)
        .attr('cy', d => d.y || 0);
    });

    function dragstarted(event: any) {
      if (!event.active) simulation.alphaTarget(0.3).restart();
      event.subject.fx = event.subject.x;
      event.subject.fy = event.subject.y;
    }

    function dragged(event: any) {
      event.subject.fx = event.x;
      event.subject.fy = event.y;
    }

    function dragended(event: any) {
      if (!event.active) simulation.alphaTarget(0);
      event.subject.fx = null;
      event.subject.fy = null;
    }

    return () => {
      simulation.stop();
    };
  }, [nodes, links]);

  return <svg ref={svgRef} width={width} height={height}></svg>;
};

export default ForceGraph;
