
using System;
using System.ServiceModel;
using Microsoft.Samples.XmlRpc;
using TinyBlogEngine;
using TinyBlogEngine.Contracts.Blogger;
namespace TinyBlogEngineClient
{
    class Program
    {
        static void Main(string[] args)
        {
            string username = "aa";
            string password = "bb";

            Uri blogAddress = new UriBuilder(Uri.UriSchemeHttp, Environment.MachineName, -1, "/blogdemo/blogger").Uri;
            
            ChannelFactory<IBloggerAPI> bloggerAPIFactory = new ChannelFactory<IBloggerAPI>(new WebHttpBinding(WebHttpSecurityMode.None), new EndpointAddress(blogAddress));
            bloggerAPIFactory.Endpoint.Behaviors.Add(new XmlRpcEndpointBehavior());

            IBloggerAPI bloggerAPI = bloggerAPIFactory.CreateChannel();

            foreach (BlogInfo info in bloggerAPI.blogger_getUsersBlogs(String.Empty, username, password))
            {
                Console.WriteLine("{0}, {1}, {2}", info.blogid, info.blogName, info.url);


                for (int i = 0; i < 10; i++)
                {
                    var newPost = new TinyBlogEngine.Contracts.MetaWeblog.Post
                    {
                        title = DateTime.UtcNow.ToString(),
                        description = "Some text",
                        dateCreated = DateTime.UtcNow,
                        categories = new string[]{"a","b","c"}
                    };
                    bloggerAPI.metaweblog_newPost(info.blogid, username, password, newPost, true);
                }

                foreach (TinyBlogEngine.Contracts.MetaWeblog.Post post in 
                         bloggerAPI.metaweblog_getRecentPosts(info.blogid, username, password, 99))
                {
                    Console.WriteLine("{0}\n{1}\n\n", post.title, post.description);
                }

                
            }
        }
    }
}
