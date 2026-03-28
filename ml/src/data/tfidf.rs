//! Sparse TF-IDF vectorization for structural duplicate detection.
//!
//! Used by `analyzer.rs` to find content overlap between legal documents
//! without requiring a GPU or neural network.

use std::collections::{HashMap, HashSet};

const STOPWORDS: &[&str] = &[
    "и", "в", "на", "с", "к", "о", "по", "из", "за", "не", "до", "от",
    "при", "для", "или", "что", "как", "так", "это", "но", "а", "же", "ли",
    "то", "бы", "был", "была", "были", "быть", "есть", "всё", "все", "он",
    "она", "они", "его", "её", "их", "мы", "вы", "им", "нам", "вам",
];

/// Builds L2-normalised TF-IDF vectors for every document in `texts`.
///
/// The stopword set is constructed once per call and reused across all
/// documents, avoiding repeated `HashSet` allocations.
pub fn vectorize(texts: &[&str]) -> Vec<HashMap<String, f32>> {
    let stops: HashSet<&str> = STOPWORDS.iter().copied().collect();
    let token_lists: Vec<Vec<String>> = texts.iter().map(|t| tokenize(t, &stops)).collect();
    let n = texts.len() as f32;

    // Document frequency: number of documents that contain each term.
    let mut df: HashMap<&str, usize> = HashMap::new();
    for tokens in &token_lists {
        let unique: HashSet<&str> = tokens.iter().map(String::as_str).collect();
        for term in unique {
            *df.entry(term).or_default() += 1;
        }
    }

    token_lists
        .iter()
        .map(|tokens| tfidf_vector(tokens, n, &df))
        .collect()
}

/// Dot product of two L2-normalised TF-IDF vectors (= cosine similarity).
pub fn cosine_similarity(a: &HashMap<String, f32>, b: &HashMap<String, f32>) -> f32 {
    // Iterate over the smaller map so the inner lookup hits the larger one.
    let (small, large) = if a.len() <= b.len() { (a, b) } else { (b, a) };
    small
        .iter()
        .filter_map(|(term, &va)| large.get(term).map(|&vb| va * vb))
        .sum()
}

// ── Private helpers ───────────────────────────────────────────────────────────

fn tfidf_vector<'a>(
    tokens: &[String],
    n: f32,
    df: &HashMap<&'a str, usize>,
) -> HashMap<String, f32> {
    let total = tokens.len() as f32;
    if total == 0.0 {
        return HashMap::new();
    }

    // Term frequency
    let mut tf: HashMap<&str, f32> = HashMap::new();
    for term in tokens {
        *tf.entry(term.as_str()).or_default() += 1.0;
    }

    // TF-IDF with smoothed IDF to avoid division by zero
    let mut vec: HashMap<String, f32> = tf
        .iter()
        .map(|(&term, &count)| {
            let idf = ((n + 1.0) / (*df.get(term).unwrap_or(&1) as f32 + 1.0)).ln() + 1.0;
            (term.to_owned(), (count / total) * idf)
        })
        .collect();

    // L2 normalisation — makes cosine similarity a simple dot product
    let norm: f32 = vec.values().map(|v| v * v).sum::<f32>().sqrt();
    if norm > 0.0 {
        vec.values_mut().for_each(|v| *v /= norm);
    }

    vec
}

fn tokenize(text: &str, stops: &HashSet<&str>) -> Vec<String> {
    text.split(|c: char| !c.is_alphanumeric())
        .map(str::trim)
        .filter(|t| t.len() >= 3)
        .map(|t| t.to_lowercase())
        .filter(|t| !stops.contains(t.as_str()))
        .collect()
}
