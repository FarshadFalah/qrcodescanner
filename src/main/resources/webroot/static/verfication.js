function verify(qrcode) {
  fetch(`/qrvalidate?qrcode=${qrcode}`)
    .then(response => response.json())
    .then(data => {
      const resultDiv = document.getElementById("result");
      resultDiv.innerHTML = data.exists ?
        (data.isInside ?
          '<span style="color: yellow;">شخص داخل است</span>' : //if Inside and Exists is True
          '<span style="color: green;">بلیط صحیح است</span>'): //if Inside is false and Exists is True
        '<span style="color: red;">بلیط مشکل دارد</span>';  //if Exists is false
    })

}
