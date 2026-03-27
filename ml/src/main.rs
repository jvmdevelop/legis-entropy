use crate::data::parser::parse_raw_document;

mod data;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let url = "https://adilet.zan.kz/rus/docs/K950001000_";
    let (title, text, status) = parse_raw_document(url).await?;

    println!("Заголовок: {}", title);
    println!("Статус: {:?}", status);
    println!("Длина текста: {} символов", text.len());

    Ok(())
}
