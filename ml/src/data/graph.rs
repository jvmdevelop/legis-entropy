//! Pure graph assembly: turns a flat list of `DocumentMeta` + `Issue`s into
//! `GraphData` ready for JSON serialisation. No I/O, no BFS (that lives in
//! the worker now).

use std::collections::{HashMap, HashSet};

use super::model::{DocumentMeta, GraphData, GraphLink, GraphNode, Issue};

pub fn assemble_graph(docs: Vec<DocumentMeta>, issues: Vec<Issue>) -> GraphData {
    // Index of all doc IDs that are in the graph
    let all_ids: HashSet<&str> = docs.iter().map(|d| d.id.as_str()).collect();

    // Count issues per document for the frontend colour-coding
    let mut issue_counts: HashMap<&str, usize> = HashMap::new();
    for issue in &issues {
        for id in &issue.document_ids {
            *issue_counts.entry(id.as_str()).or_default() += 1;
        }
    }

    // Build nodes
    let nodes: Vec<GraphNode> = docs
        .iter()
        .map(|meta| {
            let count = issue_counts.get(meta.id.as_str()).copied().unwrap_or(0);
            GraphNode::from_meta(meta, count)
        })
        .collect();

    // Build links (only between docs that are both in the graph)
    let mut links: Vec<GraphLink> = Vec::new();
    for doc in &docs {
        for ref_id in &doc.references {
            if all_ids.contains(ref_id.as_str()) {
                links.push(GraphLink {
                    source: doc.id.as_str().to_owned(),
                    target: ref_id.as_str().to_owned(),
                });
            }
        }
    }

    GraphData {
        nodes,
        links,
        issues,
    }
}
