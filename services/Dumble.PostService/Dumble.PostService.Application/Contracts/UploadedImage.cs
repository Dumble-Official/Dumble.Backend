namespace Dumble.PostService.Application.Contracts;

/// <summary>
/// Plain DTO carrying an uploaded image into the application layer without
/// leaking ASP.NET's IFormFile. Length is captured from the multipart frame
/// (IFormFile.Length) so the handler can reject oversized files before
/// reading the stream into memory.
/// </summary>
public sealed record UploadedImage(Stream Content, string FileName, string ContentType, long Length);
