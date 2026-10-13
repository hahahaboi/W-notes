# Implementation Plan: Helix-First Geometry Refactor

Completely replace the current graph-like Note Network layout with a strict helix-first architecture. The structure will be a continuous vertical sequence of oblong rectangular loops twisting into each other, with content mapped to their specific geometric regions.

## User Review Required

> [!IMPORTANT]
> This is a total replacement of the layout algorithm. I will first implement a perfectly structured 2D version to verify the geometry, then re-apply the semi-3D transformations.

> [!NOTE]
> Notes will be strictly attached to the borders, and tags will be strictly contained within the loop interiors.

## Proposed Changes

### Helix Geometry Engine

- **Oblong Loop Definition**: Each day is a horizontal oblong frame.
- **Dynamic Width**: Loop width $W = \text{baseWidth} + (\text{noteCount} \times \text{widthPerNote})$.
- **Deterministic Placement**:
    - **Notes**: Assigned positions along the loop perimeter path based on chronological order.
    - **Tags**: Distributed within the loop's inner bounding box.
    - **Intersections**: Fixed points between Day $N$ and Day $N+1$ where strands cross.

### Rendering Logic (`NetworkHelixRenderer.kt`)

#### 1. Geometry Pass
- Draw the oblong rectangular loops for each day.
- Draw the "twist" strands connecting the corners of adjacent loops.
- Mark intersection points for organizational metadata (folders/staples).

#### 2. Content Pass
- **Notes**: Draw note nodes and titles strictly on the calculated perimeter points.
- **Tags**: Draw tag anchors inside the loops.
- **Relationships**: Draw lines from perimeter notes to their respective interior tag anchors.

#### 3. Interaction & Visuals
- **Semantic Labels**: Implement note title fallback (first meaningful words) and zoom-based scaling.
- **Semi-3D**: Apply perspective and rotation only after the 2D geometry is verified.

## Verification Plan

### Milestone 1: 2D Geometry
- Verify that the helix looks like a stack of connected boxes with an "X" twist between them.
- Confirm notes are on the border and tags are inside.

### Milestone 2: Semi-3D
- Enable horizontal rotation and depth-based scaling.
- Verify perspective doesn't break the structural understanding of the timeline.
